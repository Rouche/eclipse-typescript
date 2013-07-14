/*
 * Copyright 2013 Palantir Technologies, Inc.
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

package com.palantir.typescript.text;

import org.eclipse.jface.text.DefaultIndentLineAutoEditStrategy;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.ITextDoubleClickStrategy;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.editors.text.TextSourceViewerConfiguration;

import com.google.common.base.Preconditions;

/**
 * Configures the features of the editor. This is the entry point for features like intelligent
 * double click, auto completion, and syntax highlighting.
 *
 * @author tyleradams
 */
public final class TypeScriptSourceViewerConfiguration extends TextSourceViewerConfiguration {

    private final TypeScriptDoubleClickStrategy doubleClickStrategy;
    private final ClassifierScanner classifierScanner;
    private final ColorManager colorManager;
    private final JSDocScanner jsDocScanner;
    private final CommentScanner commentScanner;

    public TypeScriptSourceViewerConfiguration(ColorManager colorManager) {
        Preconditions.checkNotNull(colorManager);

        this.colorManager = colorManager;
        this.classifierScanner = new ClassifierScanner(colorManager);
        this.jsDocScanner = new JSDocScanner(colorManager);
        this.commentScanner = new CommentScanner(colorManager);
        this.doubleClickStrategy = new TypeScriptDoubleClickStrategy();
    }

    @Override
    public IAutoEditStrategy[] getAutoEditStrategies(ISourceViewer sourceViewer, String contentType) {
        return new IAutoEditStrategy[] { new DefaultIndentLineAutoEditStrategy() };
    }

    @Override
    public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
        return new String[] {
                IDocument.DEFAULT_CONTENT_TYPE,
                TypeScriptPartitionScanner.JSDOC,
                TypeScriptPartitionScanner.MULTILINE_COMMENT,
                TypeScriptPartitionScanner.SINGLE_LINE_COMMENT
        };
    }

    @Override
    public ITextDoubleClickStrategy getDoubleClickStrategy(ISourceViewer sourceViewer, String contentType) {
        return this.doubleClickStrategy;
    }

    @Override
    public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
        Preconditions.checkNotNull(sourceViewer);

        PresentationReconciler reconciler = new PresentationReconciler();

        // default
        DefaultDamagerRepairer defaultDamagerRepairer = new DefaultDamagerRepairer(this.classifierScanner);
        reconciler.setDamager(defaultDamagerRepairer, IDocument.DEFAULT_CONTENT_TYPE);
        reconciler.setRepairer(defaultDamagerRepairer, IDocument.DEFAULT_CONTENT_TYPE);

        /*
         * It should be noted here that Eclipse deals with the highlighting of JSDOC comments and multiline comments.  TypeScript can handle the rest.
         *
         * Why not JSDOC?
         *      TypeScript doesn't know anything about JSDOC.  Thus Eclipse has to take care of it.
         * Why not multiline?
         *   Short answer: The defaultDamagerRepairer incorrectly calculates the damaged regions.
         *   Long answer:
         *     In the defaultDamagerRepairer when you break a multiline comment, eclipse can't tell that it's a mutliline comment and therefore asks typescript to repair just that line.
         *     This is not enough information for typescript to know it's in the middle of a multiline comment.  Therefore Eclipse needs to take care of it.
         *
         *     There are many known bugs which are believed to be related to this partitioning.  The recommended fix is to make a better damagerRepairer.
         */

        // JSDoc
        DefaultDamagerRepairer jsdocDamagerRepairer = new DefaultDamagerRepairer(this.jsDocScanner);
        reconciler.setDamager(jsdocDamagerRepairer, TypeScriptPartitionScanner.JSDOC);
        reconciler.setRepairer(jsdocDamagerRepairer, TypeScriptPartitionScanner.JSDOC);

        // multiline comments
        DefaultDamagerRepairer multilineDamagerRepairer = new DefaultDamagerRepairer(this.commentScanner);
        reconciler.setDamager(multilineDamagerRepairer, TypeScriptPartitionScanner.MULTILINE_COMMENT);
        reconciler.setRepairer(multilineDamagerRepairer, TypeScriptPartitionScanner.MULTILINE_COMMENT);

        // singleline comments
        DefaultDamagerRepairer singleLineDamagerRepairer = new DefaultDamagerRepairer(this.commentScanner);
        reconciler.setDamager(singleLineDamagerRepairer, TypeScriptPartitionScanner.SINGLE_LINE_COMMENT);
        reconciler.setRepairer(singleLineDamagerRepairer, TypeScriptPartitionScanner.SINGLE_LINE_COMMENT);

        return reconciler;
    }

    @Override
    public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
        Preconditions.checkNotNull(sourceViewer);

        ContentAssistant assistant = new ContentAssistant();

        assistant.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
        assistant.setContentAssistProcessor(new TypeScriptCompletionProcessor(), IDocument.DEFAULT_CONTENT_TYPE);

        assistant.enableAutoActivation(true);
        assistant.setAutoActivationDelay(100);
        assistant.setProposalPopupOrientation(IContentAssistant.PROPOSAL_OVERLAY);
        assistant.setProposalSelectorBackground(this.colorManager.getColor(TypeScriptColorConstants.AUTO_COMPLETE_BACKGROUND));
        Shell parent = null;
        IInformationControlCreator creator = new DefaultInformationControl(parent).getInformationPresenterControlCreator();
        assistant.setInformationControlCreator(creator); //TODO: Why does this work?
        return assistant;
    }
}
