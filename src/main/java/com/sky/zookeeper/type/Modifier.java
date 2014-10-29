package com.sky.zookeeper.type;

import java.lang.reflect.Constructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

public abstract class Modifier {
	protected static final Logger LOGGER = LoggerFactory.getLogger(Modifier.class);

	private SubscribeType subscribeType;
	private CreateStrategy createStrategy;

	protected Constructor<?> constructor;
	protected ApplicationContext applicationContext;

	protected Object object;

	public CreateStrategy getCreateStrategy() {
		return createStrategy;
	}
	
	public void setCreateStrategy(CreateStrategy createStrategy) {
		this.createStrategy = createStrategy;
	}

	public SubscribeType getSubscribeType() {
		return subscribeType;
	}

	public void setSubscribeType(SubscribeType subscribeType) {
		this.subscribeType = subscribeType;
	}
}
