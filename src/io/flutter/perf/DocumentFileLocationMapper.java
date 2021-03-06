/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.jetbrains.lang.dart.psi.DartId;
import com.jetbrains.lang.dart.psi.DartReferenceExpression;
import io.flutter.inspector.InspectorService;
import org.jetbrains.annotations.Nullable;

public class DocumentFileLocationMapper implements FileLocationMapper {
  @Nullable private final Document document;

  private final PsiFile psiFile;
  private final VirtualFile virtualFile;
  private final XDebuggerUtil debuggerUtil;

  public DocumentFileLocationMapper(String path, Project project) {
    this(lookupDocument(path, project), project);
  }

  @Nullable
  public static Document lookupDocument(String path, Project project) {
    final String fileName = InspectorService.fromSourceLocationUri(path, project);

    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(fileName);
    if (virtualFile != null && virtualFile.exists() &&
        !(virtualFile instanceof LightVirtualFile) && !(virtualFile instanceof HttpVirtualFile)) {
      return FileDocumentManager.getInstance().getDocument(virtualFile);
    }

    return null;
  }

  DocumentFileLocationMapper(@Nullable Document document, Project project) {
    this.document = document;

    if (document != null) {
      psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
      virtualFile = psiFile != null ? psiFile.getVirtualFile() : null;
      debuggerUtil = XDebuggerUtil.getInstance();
    }
    else {
      psiFile = null;
      virtualFile = null;
      debuggerUtil = null;
    }
  }

  @Nullable
  @Override
  public TextRange getIdentifierRange(int line, int column) {
    if (psiFile == null) {
      return null;
    }

    // Convert to zero based line and column indices.
    line = line - 1;
    column = column - 1;

    if (document == null || line >= document.getLineCount() || document.isLineModified(line)) {
      return null;
    }

    final XSourcePosition pos = debuggerUtil.createPosition(virtualFile, line, column);
    if (pos == null) {
      return null;
    }
    final int offset = pos.getOffset();
    PsiElement element = psiFile.getOriginalFile().findElementAt(offset);
    if (element == null) {
      return null;
    }

    // Handle named constructors gracefully. For example, for the constructor
    // Image.asset(...) we want to return "Image.asset" instead of "asset".
    if (element.getParent() instanceof DartId) {
      element = element.getParent();
    }
    while (element.getParent() instanceof DartReferenceExpression) {
      element = element.getParent();
    }
    return element.getTextRange();
  }

  @Nullable
  @Override
  public String getText(@Nullable TextRange textRange) {
    if (document == null || textRange == null) {
      return null;
    }
    return document.getText(textRange);
  }

  @Override
  public String getPath() {
    return psiFile == null ? null : psiFile.getVirtualFile().getPath();
  }
}
