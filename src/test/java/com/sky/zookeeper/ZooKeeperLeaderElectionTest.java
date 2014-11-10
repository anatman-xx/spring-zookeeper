package com.sky.zookeeper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;

import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.framework.recipes.leader.LeaderSelector;
import com.netflix.curator.framework.recipes.leader.LeaderSelectorListener;
import com.netflix.curator.framework.state.ConnectionState;
import com.netflix.curator.retry.RetryNTimes;
import com.sky.zookeeper.annotation.ZkLeader;
import com.sky.zookeeper.annotation.ZkManage;

@ZkManage
@Component
public class ZooKeeperLeaderElectionTest extends ZkContext {
	private static final Logger LOGGER = LoggerFactory.getLogger(ZooKeeperLeaderElectionTest.class);

	@ZkLeader(value = "/member")
	private Boolean isLeader;

	@Override
	public String getZkConnection() {
		return "localhost:2181";
	}

	@Override
	public Integer getZkConnectionTimeout() {
		return 1000;
	}
	
	public Boolean isLeader() {
		return isLeader;
	}

	public static void main(String[] args) {
		LOGGER.info("start another leader selector...");
		CuratorFramework zkClient = CuratorFrameworkFactory.builder()
				.connectString("localhost:2181")
				.connectionTimeoutMs(1000)
				.retryPolicy(new RetryNTimes(100, 10000))
				.build();
		zkClient.start();
		LeaderSelector leaderSelector = new LeaderSelector(zkClient, "/member", new LeaderSelectorListener() {
			@Override
			public void stateChanged(CuratorFramework client, ConnectionState newState) {
				LOGGER.info("state has changed to (another leader selector) " + newState);
			}
			
			@Override
			public void takeLeadership(CuratorFramework client) throws Exception {
				LOGGER.info("take leader ship(another leader selector)...");
				
				Thread.sleep(15000);
				
				LOGGER.info("release leader ship(another leader selector)...");
			}
		});
		leaderSelector.start();

		LOGGER.info("another leader selector " + leaderSelector.hasLeadership());
		
		LOGGER.info("sleep...");
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		LOGGER.info("initial...");
		ApplicationContext applicationContext = new ClassPathXmlApplicationContext("spring.xml");
		ZooKeeperLeaderElectionTest bean1 = (ZooKeeperLeaderElectionTest) applicationContext
				.getBean(ZooKeeperLeaderElectionTest.class);
		
		LOGGER.info("sleep...");
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}

		LOGGER.info("another leader selector " + leaderSelector.hasLeadership());
		LOGGER.info("bean1 " + bean1.isLeader());

		LOGGER.info("sleep...");
		try {
			Thread.sleep(100000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
