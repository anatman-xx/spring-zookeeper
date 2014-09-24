package com.futureseeds.zookeeper.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.futureseeds.zookeeper.type.CreateStrategy;
import com.futureseeds.zookeeper.type.SubscribeType;

/**
 * Indicate data source, subscribe type and creation strategy
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ZkValue {
	/**
	 * ZK path where that data stored
	 * @return
	 */
	String value();

	/**
	 * Describe what kind of subscription you subscibe 
	 * @return
	 */
	SubscribeType subscribeType() default SubscribeType.NONE;

	/**
	 * Descibe how to construct new value
	 * @return
	 */
	CreateStrategy createStrategy() default CreateStrategy.CONSTRUCTOR;
}