package com.sky.zookeeper.handler;

import java.util.Set;

import org.apache.zookeeper.WatchedEvent;

import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.api.CuratorWatcher;
import com.sky.zookeeper.type.FieldEditor;

public class ZkDataChangeEventHandler implements CuratorWatcher {
	private CuratorFramework zkClient;
	private Set<FieldEditor> fieldEditorSet;

	public ZkDataChangeEventHandler(CuratorFramework zkClient, Set<FieldEditor> fieldEditorSet) {
		this.zkClient = zkClient;
		this.fieldEditorSet = fieldEditorSet;
	}

	@Override
	public void process(WatchedEvent event) throws Exception {
		switch (event.getType()) {
		case NodeDataChanged:
			
			for (FieldEditor fieldEditor : fieldEditorSet) {
				fieldEditor.set(new String(zkClient.getData()
						.usingWatcher(this)
						.forPath(event.getPath())));
			}

			break;

		default:
			break;
		}
	}
}