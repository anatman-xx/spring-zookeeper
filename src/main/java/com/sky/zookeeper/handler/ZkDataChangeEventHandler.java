package com.sky.zookeeper.handler;

import java.util.Set;

import org.apache.zookeeper.WatchedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.api.CuratorWatcher;
import com.sky.zookeeper.type.FieldEditor;

public class ZkDataChangeEventHandler implements CuratorWatcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(ZkDataChangeEventHandler.class);

	private CuratorFramework zkClient;
	private Set<FieldEditor> fieldEditorSet;

	public ZkDataChangeEventHandler(CuratorFramework zkClient, Set<FieldEditor> fieldEditorSet) {
		this.zkClient = zkClient;
		this.fieldEditorSet = fieldEditorSet;
	}

	@Override
	public void process(WatchedEvent event) throws Exception {
		LOGGER.trace("receive event(" + event + ")");

		switch (event.getType()) {
		case NodeDataChanged:
			String newValue = new String(zkClient.getData()
						.usingWatcher(this)
						.forPath(event.getPath()));

			for (FieldEditor fieldEditor : fieldEditorSet) {
				fieldEditor.set(newValue);
			}

			break;

		default:
			break;
		}
	}
}