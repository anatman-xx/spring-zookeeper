package com.sky.zookeeper;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;

import com.sky.zookeeper.annotation.ZkManage;
import com.sky.zookeeper.annotation.ZkValue;
import com.sky.zookeeper.type.CreateStrategy;
import com.sky.zookeeper.type.SubscribeType;

@ZkManage
@Component
public class ZooKeeperAnnotationTest {
	@ZkValue(value = "", subscribeType = SubscribeType.DATA_CHANGE, createStrategy = CreateStrategy.CONSTRUCTOR)
	private String value;

	public static void main(String[] args) {
		ApplicationContext applicationContext = new ClassPathXmlApplicationContext("spring-zookeeper.xml");
		applicationContext.getBean("");
	}
}
