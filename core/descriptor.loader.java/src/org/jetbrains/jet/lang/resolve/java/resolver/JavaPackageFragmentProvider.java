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

package org.jetbrains.jet.lang.resolve.java.resolver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.descriptors.annotations.AnnotationDescriptor;
import org.jetbrains.jet.lang.resolve.java.*;
import org.jetbrains.jet.lang.resolve.java.descriptor.JavaNamespaceDescriptor;
import org.jetbrains.jet.lang.resolve.java.mapping.JavaToKotlinClassMap;
import org.jetbrains.jet.lang.resolve.java.sam.SingleAbstractMethodUtils;
import org.jetbrains.jet.lang.resolve.java.scope.JavaClassStaticMembersScope;
import org.jetbrains.jet.lang.resolve.java.scope.JavaPackageScope;
import org.jetbrains.jet.lang.resolve.java.structure.JavaClass;
import org.jetbrains.jet.lang.resolve.java.structure.JavaField;
import org.jetbrains.jet.lang.resolve.java.structure.JavaMethod;
import org.jetbrains.jet.lang.resolve.java.structure.JavaPackage;
import org.jetbrains.jet.lang.resolve.kotlin.DeserializedDescriptorResolver;
import org.jetbrains.jet.lang.resolve.kotlin.KotlinClassFinder;
import org.jetbrains.jet.lang.resolve.kotlin.KotlinJvmBinaryClass;
import org.jetbrains.jet.lang.resolve.name.FqName;
import org.jetbrains.jet.lang.resolve.name.Name;
import org.jetbrains.jet.lang.resolve.scopes.JetScope;

import javax.inject.Inject;
import java.util.*;

public final class JavaPackageFragmentProvider implements PackageFragmentProvider {

    @NotNull
    public static final ModuleDescriptor FAKE_ROOT_MODULE = new ModuleDescriptorImpl(JavaDescriptorResolver.JAVA_ROOT,
                                                                                     JavaBridgeConfiguration.ALL_JAVA_IMPORTS,
                                                                                     JavaToKotlinClassMap.getInstance());
    @NotNull
    private final Map<FqName, JetScope> packageFragments = new HashMap<FqName, JetScope>();
    @NotNull
    private final Set<FqName> unresolvedCache = new HashSet<FqName>();

    private JavaClassFinder javaClassFinder;
    private JavaResolverCache cache;
    private JavaMemberResolver memberResolver;

    private DeserializedDescriptorResolver deserializedDescriptorResolver;
    private KotlinClassFinder kotlinClassFinder;

    @Inject
    public void setKotlinClassFinder(KotlinClassFinder kotlinClassFinder) {
        this.kotlinClassFinder = kotlinClassFinder;
    }

    @Inject
    public void setJavaClassFinder(JavaClassFinder javaClassFinder) {
        this.javaClassFinder = javaClassFinder;
    }

    @Inject
    public void setCache(JavaResolverCache cache) {
        this.cache = cache;
    }

    @Inject
    public void setMemberResolver(@NotNull JavaMemberResolver memberResolver) {
        this.memberResolver = memberResolver;
    }

    @Inject
    public void setDeserializedDescriptorResolver(DeserializedDescriptorResolver deserializedDescriptorResolver) {
        this.deserializedDescriptorResolver = deserializedDescriptorResolver;
    }

    @Nullable
    private PackageFragmentDescriptor createPackageFragment(@NotNull FqName qualifiedName) {
        if (unresolvedCache.contains(qualifiedName)) {
            return null;
        }
        JetScope scope = packageFragments.get(qualifiedName);
        if (scope != null) {
            return (PackageFragmentDescriptor) scope.getContainingDeclaration();
        }

        JavaNamespaceDescriptor javaNamespaceDescriptor = new JavaNamespaceDescriptor(
                parentNs,
                Collections.<AnnotationDescriptor>emptyList(), // TODO
                qualifiedName
        );

        JetScope namespaceScope = createPackageScope(qualifiedName, javaNamespaceDescriptor, true);
        cache(qualifiedName, namespaceScope);
        if (namespaceScope == null) {
            return null;
        }

        javaNamespaceDescriptor.setMemberScope(namespaceScope);

        return javaNamespaceDescriptor;
    }

    @Nullable
    private JetScope createPackageScope(
            @NotNull FqName fqName,
            @NotNull PackageFragmentDescriptor packageFragment,
            boolean record
    ) {
        JavaPackage javaPackage = javaClassFinder.findPackage(fqName);
        if (javaPackage != null) {
            FqName packageClassFqName = PackageClassUtils.getPackageClassFqName(fqName);
            KotlinJvmBinaryClass kotlinClass = kotlinClassFinder.find(packageClassFqName);

            cache.recordProperPackage(packageFragment);

            if (kotlinClass != null) {
                // TODO 1
                //JetScope kotlinPackageScope = deserializedDescriptorResolver.createKotlinPackageScope(packageFragment, kotlinClass);
                //if (kotlinPackageScope != null) {
                //    return kotlinPackageScope;
                //}
            }


            // Otherwise (if psiClass is null or doesn't have a supported Kotlin annotation), it's a Java class and the package is empty
            if (record) {
                cache.recordPackage(javaPackage, packageFragment);
            }

            return new JavaPackageScope(packageFragment, javaPackage, fqName, memberResolver);
        }

        JavaClass javaClass = javaClassFinder.findClass(fqName);
        if (javaClass == null) {
            return null;
        }

        if (DescriptorResolverUtils.isCompiledKotlinClassOrPackageClass(javaClass)) {
            return null;
        }
        if (!hasStaticMembers(javaClass)) {
            return null;
        }

        cache.recordClassStaticMembersNamespace(packageFragment);

        if (record) {
            cache.recordPackage(javaClass, packageFragment);
        }

        return new JavaClassStaticMembersScope(packageFragment, javaClass, memberResolver);
    }

    private void cache(@NotNull FqName fqName, @Nullable JetScope packageScope) {
        if (packageScope == null) {
            unresolvedCache.add(fqName);
            return;
        }
        JetScope oldValue = packageFragments.put(fqName, packageScope);
        if (oldValue != null) {
            throw new IllegalStateException("rewrite at " + fqName);
        }
    }

    private static boolean hasStaticMembers(@NotNull JavaClass javaClass) {
        for (JavaMethod method : javaClass.getMethods()) {
            if (method.isStatic() && !DescriptorResolverUtils.shouldBeInEnumClassObject(method)) {
                return true;
            }
        }

        for (JavaField field : javaClass.getFields()) {
            if (field.isStatic() && !DescriptorResolverUtils.shouldBeInEnumClassObject(field)) {
                return true;
            }
        }

        for (JavaClass nestedClass : javaClass.getInnerClasses()) {
            if (SingleAbstractMethodUtils.isSamInterface(nestedClass)) {
                return true;
            }
            if (nestedClass.isStatic() && hasStaticMembers(nestedClass)) {
                return true;
            }
        }

        return false;
    }

    @NotNull
    public Collection<Name> getClassNamesInPackage(@NotNull FqName packageName) {
        JavaPackage javaPackage = javaClassFinder.findPackage(packageName);
        if (javaPackage == null) return Collections.emptyList();

        Collection<JavaClass> classes = DescriptorResolverUtils.getClassesInPackage(javaPackage);
        List<Name> result = new ArrayList<Name>(classes.size());
        for (JavaClass javaClass : classes) {
            if (DescriptorResolverUtils.isCompiledKotlinClass(javaClass)) {
                result.add(javaClass.getName());
            }
        }

        return result;
    }

    @NotNull
    @Override
    public List<PackageFragmentDescriptor> getPackageFragments(@NotNull FqName fqName) {
        return null;  //TODO
    }

    @NotNull
    @Override
    public Collection<FqName> getSubPackagesOf(@NotNull FqName fqName) {
        return null;  //TODO
    }
}
