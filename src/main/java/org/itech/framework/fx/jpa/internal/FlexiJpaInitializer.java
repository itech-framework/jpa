package org.itech.framework.fx.jpa.internal;

import org.itech.framework.fx.core.module.ComponentRegistry;
import org.itech.framework.fx.core.module.ModuleInitializer;
import org.itech.framework.fx.jpa.config.FlexiJpaConfig;

import static org.itech.framework.fx.core.processor.components_processor.ComponentProcessor.DATA_ACCESS_LEVEL;

public class FlexiJpaInitializer implements ModuleInitializer {
    @Override
    public void initialize(ComponentRegistry componentRegistry) {
        componentRegistry.registerComponent(FlexiJpaConfig.class, new FlexiJpaConfig(), DATA_ACCESS_LEVEL);
    }
}
