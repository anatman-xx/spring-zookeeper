package com.futureseeds.zookeeper.annotation;

import com.futureseeds.zookeeper.type.CreateStrategy;
import com.futureseeds.zookeeper.type.SubscribeType;

public @interface ZkValue {
	String value(); // zk path

	SubscribeType type() default SubscribeType.NONE; // specify what kind of event you want to subscribe
	CreateStrategy strategy() default CreateStrategy.CONSTRUCTOR;
}
