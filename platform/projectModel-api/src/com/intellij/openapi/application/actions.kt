/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.application

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.Computable
import javax.swing.SwingUtilities

inline fun <T> runWriteAction(undoTransparent: Boolean = false, crossinline runnable: () -> T): T {
  val app = ApplicationManager.getApplication()
  if (undoTransparent) {
    var result: T? = null
    CommandProcessor.getInstance().runUndoTransparentAction { result = app.runWriteAction(Computable { runnable() }) }
    return result as T
  }
  return app.runWriteAction(Computable { runnable() })
}

inline fun <T> runReadAction(crossinline runnable: () -> T): T = ApplicationManager.getApplication().runReadAction(
  Computable { runnable() })

/**
 * @exclude Internal use only
 */
fun <T> invokeAndWaitIfNeed(modalityState: ModalityState? = null, runnable: () -> T): T {
  val app = ApplicationManager.getApplication()
  if (app == null) {
    if (SwingUtilities.isEventDispatchThread()) {
      return runnable()
    }
    else {
      var result: T? = null
      SwingUtilities.invokeAndWait { result = runnable() }
      return result as T
    }
  }
  else {
    var result: T? = null
    app.invokeAndWait({ result = runnable() }, modalityState ?: ModalityState.defaultModalityState())
    return result as T
  }
}