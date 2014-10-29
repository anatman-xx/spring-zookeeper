package com.sky.zookeeper.handler;

import java.util.Set;

import org.apache.zookeeper.WatchedEvent;

import com.netflix.curator.framework.api.CuratorWatcher;
import com.sky.zookeeper.type.FieldEditor;

/**
 * empty event handler
 */
public class ZkDataDeletedEventHandler implements CuratorWatcher {
	@SuppressWarnings("unused")
	private Set<FieldEditor> fieldEditorSet;

	public ZkDataDeletedEventHandler(Set<FieldEditor> fieldEditorSet) {
		this.fieldEditorSet = fieldEditorSet;
	}

	@Override
	public void process(WatchedEvent event) throws Exception {
	}
}