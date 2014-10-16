package com.sky.zookeeper.handler;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.recipes.leader.LeaderSelectorListener;
import com.netflix.curator.framework.state.ConnectionState;
import com.sky.zookeeper.type.FieldEditor;

public class ZkLeaderHandler implements LeaderSelectorListener {
	private static final Logger LOGGER = LoggerFactory.getLogger(ZkDataChangeEventHandler.class);
	
	private String zkPath;
	private Set<FieldEditor> fieldEditorSet;

	public ZkLeaderHandler(String zkPath, Set<FieldEditor> fieldEditorSet) {
		this.zkPath = zkPath;
		this.fieldEditorSet = fieldEditorSet;
	}

	@Override
	public void stateChanged(CuratorFramework client, ConnectionState newState) {
		LOGGER.trace("state changed");
	}

	@Override
	public void takeLeadership(CuratorFramework client) throws Exception {
		LOGGER.trace("take leader ship(" + zkPath + ")");
		
		for (FieldEditor fieldEditor : fieldEditorSet) {
			fieldEditor.set("true");
		}

		client.wait();
	}
}