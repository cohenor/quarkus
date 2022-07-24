package io.quarkus.test.junit;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.quarkus.test.common.TestScopeManager;

import java.io.IOException;
import java.util.Map;

public class QuarkusMainServerIntegrationTestExtension extends AbstractQuarkusMainIntegrationTestExtension
        implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback {

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        prepare(extensionContext);
        doProcessStart(extensionContext, (Map<String, String> properties) -> {
            try {
                IntegrationTestUtil.startLauncher(launcher, properties, null);

                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        TestScopeManager.setup(true);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        launcher.close();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        TestScopeManager.tearDown(true);
    }
}
