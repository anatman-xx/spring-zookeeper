package com.sky.zookeeper.handler;

import java.util.Set;

import org.apache.zookeeper.WatchedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.api.CuratorWatcher;
import com.sky.zookeeper.type.FieldEditor;
import com.sky.zookeeper.type.MethodInvoker;

public class ZkDataChangeEventHandler implements CuratorWatcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(ZkDataChangeEventHandler.class);

	private CuratorFramework zkClient;
	private Set<?> modifiorSet;

	public ZkDataChangeEventHandler(CuratorFramework zkClient, Set<?> modifiorSet) {
		this.zkClient = zkClient;
		this.modifiorSet = modifiorSet;
	}

	@Override
	public void process(WatchedEvent event) throws Exception {
		LOGGER.trace("receive event(" + event + ")");

		switch (event.getType()) {
		case NodeDataChanged:
			String newValue = new String(zkClient.getData()
						.usingWatcher(this)
						.forPath(event.getPath()));

			for (Object modifior : modifiorSet) {
				if (modifior instanceof FieldEditor) {
					((FieldEditor) modifior).set(newValue);
				} else if (modifior instanceof MethodInvoker) {
					((MethodInvoker) modifior).invoke(newValue);
				}
			}

			break;

		default:
			break;
		}
	}
}