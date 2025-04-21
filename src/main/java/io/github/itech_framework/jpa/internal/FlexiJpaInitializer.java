package io.github.itech_framework.jpa.internal;

import io.github.itech_framework.core.module.ComponentRegistry;
import io.github.itech_framework.core.module.ModuleInitializer;
import io.github.itech_framework.jpa.config.FlexiJpaConfig;

import static io.github.itech_framework.core.processor.components_processor.ComponentProcessor.DATA_ACCESS_LEVEL;

public class FlexiJpaInitializer implements ModuleInitializer {
    @Override
    public void initialize(ComponentRegistry componentRegistry) {
        componentRegistry.registerComponent(FlexiJpaConfig.class, new FlexiJpaConfig(), DATA_ACCESS_LEVEL);
    }
}
