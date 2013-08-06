/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.util.FileContentUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.references.Injectable;
import org.intellij.plugins.intelliLang.references.InjectedReferencesContributor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class InjectLanguageAction implements IntentionAction {
  @NonNls private static final String INJECT_LANGUAGE_FAMILY = "Inject Language/Reference";

  @NotNull
  public String getText() {
    return INJECT_LANGUAGE_FAMILY;
  }

  @NotNull
  public String getFamilyName() {
    return INJECT_LANGUAGE_FAMILY;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    if (host == null) return false;
    final List<Pair<PsiElement, TextRange>> injectedPsi = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(host);
    if (injectedPsi == null || injectedPsi.isEmpty()) {
      return !InjectedReferencesContributor.isInjected(file.findReferenceAt(editor.getCaretModel().getOffset()));
    }
    return true;
  }

  @Nullable
  protected static PsiLanguageInjectionHost findInjectionHost(Editor editor, PsiFile file) {
    if (editor instanceof EditorWindow) return null;
    final int offset = editor.getCaretModel().getOffset();
    final PsiLanguageInjectionHost host = PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiLanguageInjectionHost.class, false);
    if (host == null) return null;
    return host.isValidHost()? host : null;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    doChooseLanguageToInject(editor, new Processor<Injectable>() {
      public boolean process(final Injectable injectable) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            if (!project.isDisposed()) {
              invokeImpl(project, editor, file, injectable);
            }
          }
        });
        return false;
      }
    });
  }

  public static void invokeImpl(Project project, Editor editor, PsiFile file, Injectable injectable) {
    final PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    if (host == null) return;
    if (defaultFunctionalityWorked(host, injectable.getId())) return;

    try {
      Language language = injectable.toLanguage();
      for (LanguageInjectionSupport support : InjectorUtils.getActiveInjectionSupports()) {
        if (support.addInjectionInPlace(language, host)) {
          ((PsiModificationTrackerImpl)PsiManager.getInstance(project).getModificationTracker()).incCounter();
          return;
        }
      }
      TemporaryPlacesRegistry.getInstance(project).getLanguageInjectionSupport().addInjectionInPlace(language, host);
    }
    finally {
      if (injectable.getLanguage() != null) {    // no need for reference injection
        FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
      }
    }
  }

  private static boolean defaultFunctionalityWorked(final PsiLanguageInjectionHost host, String id) {
    return Configuration.getProjectInstance(host.getProject()).setHostInjectionEnabled(host, Collections.singleton(id), true);
  }

  private static boolean doChooseLanguageToInject(Editor editor, final Processor<Injectable> onChosen) {
    final List<Injectable> injectables = Injectable.getAllInjectables();

    final JList list = new JBList(injectables);
    list.setCellRenderer(new ListCellRendererWrapper<Injectable>() {
      @Override
      public void customize(JList list, Injectable language, int index, boolean selected, boolean hasFocus) {
        setIcon(language.getIcon());
        setText(language.getDisplayName());
      }
    });
    new PopupChooserBuilder(list).setItemChoosenCallback(new Runnable() {
      public void run() {
        onChosen.process((Injectable)list.getSelectedValue());
      }
    }).setFilteringEnabled(new Function<Object, String>() {
      @Override
      public String fun(Object language) {
        return ((Injectable)language).getDisplayName();
      }
    })
      .createPopup().showInBestPositionFor(editor);
    return true;
  }

  public boolean startInWriteAction() {
    return false;
  }

  public static boolean doEditConfigurable(final Project project, final Configurable configurable) {
    return true; //ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
  }
}
