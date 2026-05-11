package com.einvoice.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Constitutional enforcement test for Wave 6 compound-tenancy filtering.
 * Scans operational repositories and service classes to prevent direct
 * findById/findAll/existsById/deleteById calls that bypass
 * OperationalRepositorySupport.inActiveTenant().
 *
 * <p>The service-call scanner uses ASM bytecode analysis to detect
 * INVOKEINTERFACE / INVOKEVIRTUAL instructions targeting forbidden
 * repository methods on operational repo interfaces.</p>
 *
 * <p>Package scanning recurses into subdirectories so that services
 * placed in conventional {@code …/service/} subpackages are
 * discovered automatically.</p>
 */
class Wave6CompoundFilterEnforcementTest {

    private static final Set<String> REPO_PACKAGES = Set.of(
            "com.einvoice.core.repository.eta",
            "com.einvoice.core.repository.zatca",
            "com.einvoice.core.repository.config"
    );

    private static final Set<String> SERVICE_PACKAGES = Set.of(
            "com.einvoice.api.eta",
            "com.einvoice.api.zatca",
            "com.einvoice.api.config"
    );

    private static final Set<String> FORBIDDEN_METHODS = Set.of(
            "findById", "findAll", "existsById", "deleteById",
            "getById", "getReferenceById", "count"
    );

    @Test
    void queryAnnotatedRepoMethods_bindBothTenantParameters() {
        for (String pkg : REPO_PACKAGES) {
            List<Class<?>> repoClasses = findClassesInPackage(pkg).stream()
                    .filter(cls -> Repository.class.isAssignableFrom(cls))
                    .toList();
            for (Class<?> repoClass : repoClasses) {
                for (Method method : repoClass.getMethods()) {
                    Query query = method.getAnnotation(Query.class);
                    if (query == null) {
                        continue;
                    }
                    String jpql = query.value().toLowerCase();
                    boolean hasCompanyId = jpql.contains(":companyid")
                            || jpql.contains(":company_id");
                    boolean hasAuthEnvId = jpql.contains(":authorityenvironmentid")
                            || jpql.contains(":authority_environment_id");
                    assertTrue(hasCompanyId && hasAuthEnvId,
                            repoClass.getSimpleName() + "." + method.getName()
                                    + " @Query must bind both :companyId and :authorityEnvironmentId."
                                    + " Query: " + query.value());
                }
            }
        }
    }

    @Test
    void repoInterfaces_doNotDeclareForbiddenMethods() {
        for (String pkg : REPO_PACKAGES) {
            List<Class<?>> repoClasses = findClassesInPackage(pkg).stream()
                    .filter(cls -> Repository.class.isAssignableFrom(cls))
                    .toList();
            for (Class<?> repoClass : repoClasses) {
                for (Method method : repoClass.getDeclaredMethods()) {
                    assertTrue(!FORBIDDEN_METHODS.contains(method.getName()),
                            repoClass.getSimpleName() + "." + method.getName()
                                    + " is forbidden — use JpaSpecificationExecutor"
                                    + " with inActiveTenant()");
                }
            }
        }
    }

    @Test
    void serviceClasses_doNotCallForbiddenRepoMethods() {
        Set<String> repoInternalNames = collectRepoInternalNames();

        for (String pkg : SERVICE_PACKAGES) {
            List<Class<?>> serviceClasses = findClassesInPackage(pkg).stream()
                    .filter(cls -> cls.getSimpleName().endsWith("Service"))
                    .toList();

            for (Class<?> serviceClass : serviceClasses) {
                assertNoForbiddenRepoCalls(serviceClass, repoInternalNames);
            }
        }
    }

    @Test
    void forbiddenCallDetector_detectsCallOnOperationalRepo() throws Exception {
        Set<String> repoInternalNames = collectRepoInternalNames();
        assertFalse(repoInternalNames.isEmpty(),
                "Should have found at least one operational repo");

        Class<?> fixture = Class.forName(
                "com.einvoice.api.fixtures.ForbiddenCallFixture");
        byte[] bytes = readClassBytes(fixture);
        ClassReader reader = new ClassReader(bytes);
        ForbiddenCallDetector detector = new ForbiddenCallDetector(
                repoInternalNames, FORBIDDEN_METHODS);
        reader.accept(detector, 0);

        assertTrue(detector.hasForbiddenCall(),
                "ForbiddenCallFixture.loadById calls EtaCustomerRepository.findById "
                        + "— detector should flag it");
        assertTrue(FORBIDDEN_METHODS.contains(detector.getForbiddenOperation()),
                "Detected operation should be one of the forbidden methods");
    }

    @Test
    void forbiddenCallDetector_doesNotFlagCompliantClass() throws Exception {
        Set<String> repoInternalNames = collectRepoInternalNames();

        Class<?> compliant = Class.forName(
                "com.einvoice.api.fixtures.CompliantCallFixture");
        byte[] bytes = readClassBytes(compliant);
        ClassReader reader = new ClassReader(bytes);
        ForbiddenCallDetector detector = new ForbiddenCallDetector(
                repoInternalNames, FORBIDDEN_METHODS);
        reader.accept(detector, 0);

        assertFalse(detector.hasForbiddenCall(),
                "CompliantCallFixture uses only spec-based queries "
                        + "— detector should not flag it");
    }

    private Set<String> collectRepoInternalNames() {
        Set<String> names = new HashSet<>();
        for (String pkg : REPO_PACKAGES) {
            findClassesInPackage(pkg).stream()
                    .filter(cls -> Repository.class.isAssignableFrom(cls))
                    .forEach(cls -> names.add(Type.getInternalName(cls)));
        }
        return names;
    }

    private void assertNoForbiddenRepoCalls(Class<?> serviceClass,
            Set<String> repoInternalNames) {
        byte[] classBytes = readClassBytes(serviceClass);
        ClassReader reader = new ClassReader(classBytes);
        ForbiddenCallDetector detector = new ForbiddenCallDetector(
                repoInternalNames, FORBIDDEN_METHODS);
        reader.accept(detector, 0);
        assertTrue(!detector.hasForbiddenCall(),
                serviceClass.getSimpleName() + "." + detector.getEnclosingMethod()
                        + " calls " + simpleName(detector.getForbiddenOwner())
                        + "." + detector.getForbiddenOperation()
                        + " — use JpaSpecificationExecutor with inActiveTenant()");
    }

    private byte[] readClassBytes(Class<?> clazz) {
        String resource = clazz.getName().replace('.', '/') + ".class";
        try (var is = clazz.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                throw new RuntimeException("Class file not found: " + resource);
            }
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read class: " + clazz.getName(), e);
        }
    }

    private static String simpleName(String internalName) {
        if (internalName == null) {
            return "";
        }
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash >= 0 ? internalName.substring(lastSlash + 1) : internalName;
    }

    static class ForbiddenCallDetector extends ClassVisitor {

        private static final int ASM_VERSION = Opcodes.ASM9;

        private final Set<String> repoInternalNames;
        private final Set<String> forbiddenMethodNames;
        private boolean forbiddenFound;
        private String enclosingMethod;
        private String forbiddenOwner;
        private String forbiddenOperation;

        ForbiddenCallDetector(Set<String> repoInternalNames,
                Set<String> forbiddenMethodNames) {
            super(ASM_VERSION);
            this.repoInternalNames = repoInternalNames;
            this.forbiddenMethodNames = forbiddenMethodNames;
        }

        @Override
        public MethodVisitor visitMethod(int access, String outerName, String outerDescriptor,
                String sig, String[] exceptions) {
            return new MethodVisitor(ASM_VERSION) {
                @Override
                public void visitMethodInsn(int opcode, String owner,
                        String name, String descriptor, boolean isInterface) {
                    if (!forbiddenFound
                            && owner != null
                            && (opcode == Opcodes.INVOKEINTERFACE
                                || opcode == Opcodes.INVOKEVIRTUAL)
                            && repoInternalNames.contains(owner)
                            && forbiddenMethodNames.contains(name)
                            && !(name.equals("findAll") && !descriptor.startsWith("()"))) {
                        forbiddenFound = true;
                        enclosingMethod = outerName;
                        forbiddenOwner = owner;
                        forbiddenOperation = name;
                    }
                }
            };
        }

        boolean hasForbiddenCall() {
            return forbiddenFound;
        }

        String getEnclosingMethod() {
            return enclosingMethod;
        }

        String getForbiddenOwner() {
            return forbiddenOwner;
        }

        String getForbiddenOperation() {
            return forbiddenOperation;
        }
    }

    private List<Class<?>> findClassesInPackage(String packageName) {
        List<Class<?>> result = new ArrayList<>();
        try {
            String path = packageName.replace('.', '/');
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = cl.getResources(path);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                if ("file".equals(url.getProtocol())) {
                    File dir = new File(url.toURI());
                    if (dir.isDirectory()) {
                        scanDirectoryRecursive(dir, packageName, result);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan package: " + packageName, e);
        }
        return result;
    }

    private void scanDirectoryRecursive(File directory, String packageName,
            List<Class<?>> accumulator) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectoryRecursive(file,
                        packageName + "." + file.getName(), accumulator);
            } else if (file.getName().endsWith(".class")
                    && !file.getName().contains("$")) {
                String className = packageName + "."
                        + file.getName().replace(".class", "");
                try {
                    accumulator.add(Class.forName(className));
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
    }
}
