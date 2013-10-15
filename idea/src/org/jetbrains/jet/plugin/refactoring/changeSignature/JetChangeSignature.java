/*
 * Copyright 2010-2013 JetBrains s.r.o.
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

package org.jetbrains.jet.plugin.refactoring.changeSignature;

import com.google.common.collect.Lists;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.OverridingUtil;
import org.jetbrains.jet.plugin.JetBundle;
import org.jetbrains.jet.renderer.DescriptorRenderer;

import javax.swing.*;
import java.util.*;

import static org.jetbrains.jet.lang.descriptors.CallableMemberDescriptor.Kind.*;
import static org.jetbrains.jet.lang.resolve.BindingContextUtils.*;

public final class JetChangeSignature {

    public static void run(
            @NotNull Project project,
            @NotNull FunctionDescriptor functionDescriptor,
            @NotNull ChangeSignatureConfiguration configuration,
            @NotNull BindingContext bindingContext,
            @NotNull PsiElement defaultValueContext,
            @Nullable String commandName,
            boolean performSilently
    ) {
        new JetChangeSignature(project, functionDescriptor, configuration,
                               bindingContext, defaultValueContext, commandName, performSilently).run();
    }

    private static final Logger LOG = Logger.getInstance(JetChangeSignature.class);

    @NotNull private final Project project;
    @NotNull private final FunctionDescriptor functionDescriptor;
    @NotNull private final ChangeSignatureConfiguration configuration;
    @NotNull private final BindingContext bindingContext;
    @NotNull private final PsiElement defaultValueContext;
    @Nullable private final String commandName;
    private final boolean performSilently;

    private static void performRefactoringSilently(
            @NotNull final JetChangeSignatureDialog dialog,
            @NotNull final Project project,
            @Nullable final String commandName
    ) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
                JetChangeInfo changeInfo = dialog.evaluateChangeInfo();
                JetChangeSignatureProcessor processor = new JetChangeSignatureProcessor(project, changeInfo, commandName);
                processor.run();
                Disposer.dispose(dialog.getDisposable());
            }
        });
    }

    private JetChangeSignature(
            @NotNull Project project,
            @NotNull FunctionDescriptor functionDescriptor,
            @NotNull ChangeSignatureConfiguration configuration,
            @NotNull BindingContext bindingContext,
            @NotNull PsiElement defaultValueContext,
            @Nullable String commandName,
            boolean performSilently
    ) {
        this.project = project;
        this.functionDescriptor = functionDescriptor;
        this.configuration = configuration;
        this.bindingContext = bindingContext;
        this.defaultValueContext = defaultValueContext;
        this.commandName = commandName;
        this.performSilently = performSilently;
    }

    public void run() {
        assert functionDescriptor.getKind() != SYNTHESIZED : "Change signature refactoring should not be called for synthesized member";
        Collection<FunctionDescriptor> mostShallowSuperDeclarations = getMostShallowSuperDeclarations();
        Set<FunctionDescriptor> deepestSuperDeclarations = getDeepestSuperDeclarations();
        Collection<FunctionDescriptor> deepestWithoutMostShallowSuperDeclarations =
                ContainerUtil.subtract(deepestSuperDeclarations, mostShallowSuperDeclarations);
        assert !mostShallowSuperDeclarations.isEmpty() : "Should contain functionDescriptor itself or some of its super declarations";
        if (deepestWithoutMostShallowSuperDeclarations.isEmpty() && mostShallowSuperDeclarations.size() == 1) {
            showChangeSignatureDialog(mostShallowSuperDeclarations);
            return;
        }
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            showChangeSignatureDialog(deepestSuperDeclarations);
            return;
        }
        /*
        NOTE: If there is only one function selected then we can ask user if he wants to limit the effects of change signature to
                this function, otherwise we are forced to affect the whole hierarchy or do nothing at all.
                */
        boolean isSingleFunctionSelected = mostShallowSuperDeclarations.size() == 1;
        List<String> optionsForDialog = Lists.newArrayList();
        optionsForDialog.add(isSingleFunctionSelected ? Messages.YES_BUTTON : Messages.OK_BUTTON);
        if (isSingleFunctionSelected) {
            optionsForDialog.add(Messages.NO_BUTTON);
        }
        optionsForDialog.add(Messages.CANCEL_BUTTON);
        FunctionDescriptor selectedFunction = isSingleFunctionSelected
                                              ? mostShallowSuperDeclarations.iterator().next()
                                              : functionDescriptor;
        Collection<FunctionDescriptor> overriddenFunctions = isSingleFunctionSelected
                                                             ? deepestWithoutMostShallowSuperDeclarations
                                                             : deepestSuperDeclarations;
        int code = showSuperWarningDialog(overriddenFunctions, selectedFunction, ArrayUtil.toStringArray(optionsForDialog));
        if (performForWholeHierarchy(optionsForDialog, code)) {
            showChangeSignatureDialog(deepestSuperDeclarations);
        }
        else if (performForSelectedFunctionOnly(optionsForDialog, code)) {
            showChangeSignatureDialog(Collections.singleton(selectedFunction));
        }
        else {
            //do nothing
        }
    }

    private static boolean performForSelectedFunctionOnly(@NotNull List<String> dialogButtons, int code) {
        return buttonPressed(code, dialogButtons, Messages.NO_BUTTON);
    }

    private static boolean performForWholeHierarchy(@NotNull List<String> optionsForDialog, int code) {
        return buttonPressed(code, optionsForDialog, Messages.YES_BUTTON) || buttonPressed(code, optionsForDialog, Messages.OK_BUTTON);
    }

    private static boolean buttonPressed(int code, @NotNull List<String> dialogButtons, @NotNull String button) {
        return code == dialogButtons.indexOf(button) && dialogButtons.contains(button);
    }

    private static int showSuperWarningDialog(
            @NotNull Collection<FunctionDescriptor> superFunctions,
            @NotNull FunctionDescriptor functionFromEditor,
            @NotNull String[] options
    ) {
        String superString = StringUtil.join(ContainerUtil.map(superFunctions, new Function<FunctionDescriptor, String>() {
            @Override
            public String fun(FunctionDescriptor descriptor) {
                return descriptor.getContainingDeclaration().getName().asString();
            }
        }), ", ") + ".\n";
        String message = JetBundle.message("x.overrides.y.in.class.list",
                                           DescriptorRenderer.COMPACT.render(functionFromEditor),
                                           functionFromEditor.getContainingDeclaration().getName().asString(),
                                           superString, "refactor");
        String title = IdeBundle.message("title.warning");
        Icon icon = Messages.getQuestionIcon();
        return Messages.showDialog(message, title, options, 0, icon);
    }

    @TestOnly
    @Nullable
    public static JetChangeSignatureDialog getDialog(
            @NotNull Project project,
            @NotNull FunctionDescriptor functionDescriptor,
            @NotNull ChangeSignatureConfiguration configuration,
            @NotNull BindingContext bindingContext,
            @NotNull PsiElement defaultValueContext
    ) {
        JetChangeSignature jetChangeSignature = new JetChangeSignature(project, functionDescriptor, configuration,
                                                                       bindingContext, defaultValueContext, null, /**/true);
        return jetChangeSignature.createChangeSignatureDialog(jetChangeSignature.getDeepestSuperDeclarations());
    }


    private void showChangeSignatureDialog(@NotNull Collection<FunctionDescriptor> descriptorsForSignatureChange) {
        JetChangeSignatureDialog dialog = createChangeSignatureDialog(descriptorsForSignatureChange);
        if (dialog == null) {
            return;
        }
        if (performSilently || ApplicationManager.getApplication().isUnitTestMode()) {
            performRefactoringSilently(dialog, project, commandName);
        }
        else {
            dialog.show();
        }
    }

    @Nullable
    private JetChangeSignatureDialog createChangeSignatureDialog(@NotNull Collection<FunctionDescriptor> descriptorsForSignatureChange) {
        FunctionDescriptor baseDescriptor = preferContainedInClass(descriptorsForSignatureChange);
        PsiElement functionDeclaration = callableDescriptorToDeclaration(bindingContext, baseDescriptor);
        if (functionDeclaration == null) {
            LOG.error("Could not find declaration for " + baseDescriptor);
            return null;
        }
        JetChangeSignatureData changeSignatureData = new JetChangeSignatureData(baseDescriptor, functionDeclaration,
                                                                                bindingContext, descriptorsForSignatureChange);
        configuration.configure(changeSignatureData, bindingContext);
        return new JetChangeSignatureDialog(project, changeSignatureData, defaultValueContext, commandName);
    }


    @NotNull
    private static FunctionDescriptor preferContainedInClass(@NotNull Collection<FunctionDescriptor> descriptorsForSignatureChange) {
        List<FunctionDescriptor> orderedDescriptors = new ArrayList<FunctionDescriptor>(descriptorsForSignatureChange);
        List<DeclarationDescriptor> orderedContainers =
                ContainerUtil.map(orderedDescriptors, new Function<FunctionDescriptor, DeclarationDescriptor>() {
            @Override
            public DeclarationDescriptor fun(FunctionDescriptor descriptor) {
                return descriptor.getContainingDeclaration();
            }
        });
        DeclarationDescriptor containingClass = ContainerUtil.find(orderedContainers, new Condition<DeclarationDescriptor>() {
            @Override
            public boolean value(DeclarationDescriptor descriptor) {
                return descriptor instanceof ClassDescriptor && ((ClassDescriptor) descriptor).getKind() != ClassKind.TRAIT;
            }
        });
        if (containingClass != null) {
            return orderedDescriptors.get(orderedContainers.indexOf(containingClass));
        } else {
            //random descriptor
            return descriptorsForSignatureChange.iterator().next();
        }
    }

    @NotNull
    private Set<FunctionDescriptor> getMostShallowSuperDeclarations() {
        CallableMemberDescriptor.Kind kind = functionDescriptor.getKind();
        if (kind == DELEGATION || kind == FAKE_OVERRIDE) {
            return getDirectlyOverriddenDeclarations(functionDescriptor);
        }
        assert kind == DECLARATION : "Unexpected callable kind: " + kind;
        return Collections.singleton(functionDescriptor);
    }

    @NotNull
    private Set<FunctionDescriptor> getDeepestSuperDeclarations() {
        Set<FunctionDescriptor> overriddenDeclarations = getAllOverriddenDeclarations(functionDescriptor);
        if (overriddenDeclarations.isEmpty()) {
            return Collections.singleton(functionDescriptor);
        }
        return OverridingUtil.filterOutOverriding(overriddenDeclarations);
    }

    public interface ChangeSignatureConfiguration {
        void configure(@NotNull JetChangeSignatureData changeSignatureData, @NotNull BindingContext bindingContext);
    }
}
