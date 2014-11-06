package com.sky.zookeeper.watcher;

import java.util.Set;

import org.apache.zookeeper.WatchedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.api.CuratorWatcher;
import com.sky.zookeeper.type.FieldEditor;
import com.sky.zookeeper.type.MethodInvoker;
import com.sky.zookeeper.type.Modifier;

public class ZkDataChangeWatcher implements CuratorWatcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(ZkDataChangeWatcher.class);

	private CuratorFramework zkClient;
	private Set<Modifier> modifierSet;

	public ZkDataChangeWatcher(CuratorFramework zkClient, Set<Modifier> modifierSet) {
		this.zkClient = zkClient;
		this.modifierSet = modifierSet;
	}

	@Override
	public void process(WatchedEvent event) throws Exception {
		LOGGER.trace("receive event(" + event + ")");

		switch (event.getType()) {
		case NodeDataChanged:
			String newValue = new String(zkClient.getData()
						.usingWatcher(this)
						.forPath(event.getPath()));

			for (Modifier modifier : modifierSet) {
				if (modifier instanceof FieldEditor) {
					((FieldEditor) modifier).set(newValue);
				} else if (modifier instanceof MethodInvoker) {
					((MethodInvoker) modifier).invoke(newValue);
				}
			}

			break;

		default:
			break;
		}
	}
}