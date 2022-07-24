package io.quarkus.test.junit;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;

public class QuarkusMainIntegrationTestExtension extends AbstractQuarkusMainIntegrationTestExtension
    implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    LaunchResult result;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        var launch = context.getRequiredTestMethod().getAnnotation(Launch.class);
        if (launch != null) {
            String[] arguments = launch.value();
            LaunchResult r = doLaunch(context, arguments);
            Assertions.assertEquals(launch.exitCode(), r.exitCode(), "Exit code did not match");
            this.result = r;
        }
    }

    private LaunchResult doLaunch(ExtensionContext context, String[] arguments) throws Exception {
        if (quarkusArtifactProperties == null) {
            prepare(context);
        }
        var result = doProcessStart(context, (Map<String, String> unused) -> launcher.runToCompletion(arguments));

        List<String> out = Arrays.asList(new String(result.getOutput(), StandardCharsets.UTF_8).split("\n"));
        List<String> err = Arrays.asList(new String(result.getStderror(), StandardCharsets.UTF_8).split("\n"));
        return new LaunchResult() {
            @Override
            public List<String> getOutputStream() {
                return out;
            }

            @Override
            public List<String> getErrorStream() {
                return err;
            }

            @Override
            public int exitCode() {
                return result.getStatusCode();
            }
        };
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        result = null;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        return type == LaunchResult.class || type == QuarkusMainLauncher.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        if (type == LaunchResult.class) {
            return result;
        } else if (type == QuarkusMainLauncher.class) {
            return new QuarkusMainLauncher() {
                @Override
                public LaunchResult launch(String... args) {
                    try {
                        return doLaunch(extensionContext, args);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        } else {
            throw new RuntimeException("Parameter type not supported");
        }
    }
}
