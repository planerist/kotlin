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

package org.jetbrains.jet.lang.descriptors;

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.ModuleConfiguration;
import org.jetbrains.jet.lang.PlatformToKotlinClassMap;
import org.jetbrains.jet.lang.descriptors.annotations.AnnotationDescriptor;
import org.jetbrains.jet.lang.descriptors.impl.CompositePackageFragmentProvider;
import org.jetbrains.jet.lang.descriptors.impl.DeclarationDescriptorImpl;
import org.jetbrains.jet.lang.descriptors.impl.PackageViewDescriptorImpl;
import org.jetbrains.jet.lang.resolve.ImportPath;
import org.jetbrains.jet.lang.resolve.name.FqName;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.lang.types.TypeSubstitutor;

import java.util.Collections;
import java.util.List;

public class ModuleDescriptorImpl extends DeclarationDescriptorImpl implements ModuleDescriptor {
    private final List<PackageFragmentProvider> fragmentProviders = Lists.newArrayList();
    private ModuleConfiguration moduleConfiguration;
    private final List<ImportPath> defaultImports;
    private final PlatformToKotlinClassMap platformToKotlinClassMap;

    public ModuleDescriptorImpl(
            @NotNull Name name,
            @NotNull List<ImportPath> defaultImports,
            @NotNull PlatformToKotlinClassMap platformToKotlinClassMap
    ) {
        super(Collections.<AnnotationDescriptor>emptyList(), name);
        if (!name.isSpecial()) {
            throw new IllegalArgumentException("module name must be special: " + name);
        }
        this.defaultImports = defaultImports;
        this.platformToKotlinClassMap = platformToKotlinClassMap;
    }

    public void addFragmentProvider(@NotNull PackageFragmentProvider provider) {
        fragmentProviders.add(provider);
    }

    @Override
    @Nullable
    public DeclarationDescriptor getContainingDeclaration() {
        return null;
    }

    @Override
    public PackageFragmentProvider getPackageFragmentProvider() {
        return new CompositePackageFragmentProvider(fragmentProviders);
    }

    // TODO 1 creation on each call - no good
    @Nullable
    @Override
    public PackageViewDescriptor getPackage(@NotNull FqName fqName) {
        List<PackageFragmentDescriptor> fragments = getPackageFragmentProvider().getPackageFragments(fqName);
        return !fragments.isEmpty()
               ? new PackageViewDescriptorImpl(this, fqName, fragments)
               : null;
    }

    @NotNull
    @Override
    public ModuleConfiguration getModuleConfiguration() {
        return moduleConfiguration;
    }

    @NotNull
    public ModuleDescriptorImpl setModuleConfiguration(@NotNull ModuleConfiguration moduleConfiguration) {
        assert this.moduleConfiguration == null : "Trying to set module configuration twice for " + this;
        this.moduleConfiguration = moduleConfiguration;
        return this;
    }

    @NotNull
    @Override
    public List<ImportPath> getDefaultImports() {
        return defaultImports;
    }

    @NotNull
    @Override
    public PlatformToKotlinClassMap getPlatformToKotlinClassMap() {
        return platformToKotlinClassMap;
    }

    @NotNull
    @Override
    public ModuleDescriptor substitute(@NotNull TypeSubstitutor substitutor) {
        return this;
    }

    @Override
    public <R, D> R accept(DeclarationDescriptorVisitor<R, D> visitor, D data) {
        return visitor.visitModuleDeclaration(this, data);
    }
}
