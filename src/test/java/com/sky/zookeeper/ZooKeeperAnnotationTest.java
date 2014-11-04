package com.sky.zookeeper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;

import com.sky.zookeeper.annotation.ZkManage;
import com.sky.zookeeper.annotation.ZkValue;
import com.sky.zookeeper.type.CreateStrategy;
import com.sky.zookeeper.type.SubscribeType;

@ZkManage
@Component
public class ZooKeeperAnnotationTest extends ZkContext {
	private static final Logger LOGGER = LoggerFactory.getLogger(ZooKeeperAnnotationTest.class);

	@ZkValue(value = "/sms/phoneProvider", createStrategy = CreateStrategy.CONSTRUCTOR, subscribeType = SubscribeType.DATA_CHANGE)
	public void setProvider(String provider) {
		LOGGER.info("method called with argument \"" + provider + "\"");
	}
	
	@ZkValue(value = "/sms/phoneProvider", createStrategy = CreateStrategy.CONSTRUCTOR, subscribeType = SubscribeType.DATA_CHANGE)
	private String phone;

	@Override
	public String getZkConnection() {
		return "localhost:2181";
	}

	@Override
	public Integer getZkConnectionTimeout() {
		return 1000;
	}

	public String getPhone() {
		return phone;
	}

	public static void main(String[] args) {
		LOGGER.info("initial...");
		
		ApplicationContext applicationContext = new ClassPathXmlApplicationContext("spring-zookeeper.xml");
		ZooKeeperAnnotationTest bean = (ZooKeeperAnnotationTest) applicationContext.getBean("zooKeeperAnnotationTest");
		
		LOGGER.info("prev phone value " + bean.getPhone());

		LOGGER.info("sleep...");
		try {
			Thread.currentThread().sleep(10000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		LOGGER.info("change zk data...");
		try {
			bean.getZkClient().setData().forPath("/sms/phoneProvider", "123321".getBytes());
		} catch (Exception e) {
			e.printStackTrace();
		}

		LOGGER.info("current phone value " + bean.getPhone());
	}
}
