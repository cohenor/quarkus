package io.quarkus.test.junit;

import io.quarkus.test.common.ArtifactLauncher;
import io.quarkus.test.common.TestResourceManager;
import io.quarkus.test.junit.launcher.ArtifactLauncherProvider;
import io.quarkus.test.junit.util.CloseAdaptor;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.function.Function;

import static io.quarkus.test.junit.IntegrationTestUtil.*;

abstract class AbstractQuarkusMainIntegrationTestExtension {
    public static ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
        .create("io.quarkus.test.main.integration");

    protected static Map<String, String> devServicesProps;

    ArtifactLauncher.InitContext.DevServicesLaunchResult devServicesLaunchResult;
    Properties quarkusArtifactProperties;

    ArtifactLauncher<?> launcher;

    protected void prepare(ExtensionContext extensionContext) throws Exception {
        quarkusArtifactProperties = readQuarkusArtifactProperties(extensionContext);
        String artifactType = quarkusArtifactProperties.getProperty("type");
        if (artifactType == null) {
            throw new IllegalStateException("Unable to determine the type of artifact created by the Quarkus build");
        }
        boolean isDockerLaunch = "jar-container".equals(artifactType) || "native-container".equals(artifactType);

        ArtifactLauncher.InitContext.DevServicesLaunchResult devServicesLaunchResult = handleDevServices(
            extensionContext, isDockerLaunch);
        devServicesProps = devServicesLaunchResult.properties();

        ExtensionContext root = extensionContext.getRoot();
        root.getStore(NAMESPACE).put("devServicesLaunchResult", new CloseAdaptor(devServicesLaunchResult));
    }

    protected ArtifactLauncher.LaunchResult doProcessStart(ExtensionContext context,
        Function<Map<String, String>, ArtifactLauncher.LaunchResult> launch) {
        try {
            Class<? extends QuarkusTestProfile> profile = findProfile(context.getRequiredTestClass());
            TestResourceManager testResourceManager = null;
            Map<String, String> old = new HashMap<>();
            try {
                Map<String, String> sysPropRestore = getSysPropsToRestore();
                TestProfileAndProperties testProfileAndProperties = determineTestProfileAndProperties(profile,
                    sysPropRestore);

                testResourceManager = buildTestResourceManager(context, testProfileAndProperties);

                Map<String, String> additionalProperties = new HashMap<>(testProfileAndProperties.properties);
                Map<String, String> resourceManagerProps = new HashMap<>(testResourceManager.start());
                //also make the dev services props accessible from the test
                resourceManagerProps.putAll(QuarkusMainIntegrationTestExtension.devServicesProps);
                for (Map.Entry<String, String> i : resourceManagerProps.entrySet()) {
                    old.put(i.getKey(), System.getProperty(i.getKey()));
                    if (i.getValue() == null) {
                        System.clearProperty(i.getKey());
                    } else {
                        System.setProperty(i.getKey(), i.getValue());
                    }
                }
                additionalProperties.putAll(resourceManagerProps);

                testResourceManager.inject(context.getRequiredTestInstance());

                launcher = getArtifactLauncher(context);
                launcher.includeAsSysProps(additionalProperties);
                activateLogging();

                return launch.apply(additionalProperties);
            } finally {

                for (Map.Entry<String, String> i : old.entrySet()) {
                    old.put(i.getKey(), System.getProperty(i.getKey()));
                    if (i.getValue() == null) {
                        System.clearProperty(i.getKey());
                    } else {
                        System.setProperty(i.getKey(), i.getValue());
                    }
                }
                if (testResourceManager != null) {
                    testResourceManager.close();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TestResourceManager buildTestResourceManager(ExtensionContext context,
        TestProfileAndProperties testProfileAndProperties) {
        Class<? extends QuarkusTestProfile> profile = findProfile(context.getRequiredTestClass());
        Class<?> requiredTestClass = context.getRequiredTestClass();

        var testResourceManager = new TestResourceManager(requiredTestClass, profile,
            getAdditionalTestResources(testProfileAndProperties.testProfile,
                context.getRequiredTestClass().getClassLoader()),
            testProfileAndProperties.testProfile != null &&
                testProfileAndProperties.testProfile.disableGlobalTestResources());

        testResourceManager.init(testProfileAndProperties.testProfile != null ?
            testProfileAndProperties.testProfile.getClass().getName() : null);

        return testResourceManager;
    }

    protected ArtifactLauncher<?> getArtifactLauncher(ExtensionContext context) {
        String artifactType = quarkusArtifactProperties.getProperty("type");
        ArtifactLauncher<?> launcher = null;
        ServiceLoader<ArtifactLauncherProvider> loader = ServiceLoader.load(ArtifactLauncherProvider.class);
        for (ArtifactLauncherProvider launcherProvider : loader) {
            if (launcherProvider.supportsArtifactType(artifactType)) {
                launcher = launcherProvider.create(
                    new DefaultArtifactLauncherCreateContext(quarkusArtifactProperties, context,
                        context.getRequiredTestClass(), devServicesLaunchResult));
                break;
            }
        }
        if (launcher == null) {
            throw new IllegalStateException(
                "Artifact type + '" + artifactType + "' is not supported by @QuarkusMain integration tests");
        }

        return launcher;
    }

    protected static class DefaultArtifactLauncherCreateContext implements ArtifactLauncherProvider.CreateContext {
        private final Properties quarkusArtifactProperties;
        private final ExtensionContext context;
        private final Class<?> requiredTestClass;
        private final ArtifactLauncher.InitContext.DevServicesLaunchResult devServicesLaunchResult;

        DefaultArtifactLauncherCreateContext(Properties quarkusArtifactProperties, ExtensionContext context,
            Class<?> requiredTestClass, ArtifactLauncher.InitContext.DevServicesLaunchResult devServicesLaunchResult) {
            this.quarkusArtifactProperties = quarkusArtifactProperties;
            this.context = context;
            this.requiredTestClass = requiredTestClass;
            this.devServicesLaunchResult = devServicesLaunchResult;
        }

        @Override
        public Properties quarkusArtifactProperties() {
            return quarkusArtifactProperties;
        }

        @Override
        public Path buildOutputDirectory() {
            return determineBuildOutputDirectory(context);
        }

        @Override
        public Class<?> testClass() {
            return requiredTestClass;
        }

        @Override
        public ArtifactLauncher.InitContext.DevServicesLaunchResult devServicesLaunchResult() {
            return devServicesLaunchResult;
        }
    }
}
