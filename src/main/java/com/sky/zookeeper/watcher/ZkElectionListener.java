package com.sky.zookeeper.watcher;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.recipes.leader.LeaderSelectorListener;
import com.netflix.curator.framework.state.ConnectionState;
import com.sky.zookeeper.type.FieldEditor;
import com.sky.zookeeper.type.MethodInvoker;
import com.sky.zookeeper.type.Modifier;

public class ZkElectionListener implements LeaderSelectorListener {
	private static final Logger LOGGER = LoggerFactory.getLogger(ZkDataChangeWatcher.class);
	
	private String zkPath;
	private Set<Modifier> modifierSet;
	
	private Object lock = new Object();

	public ZkElectionListener(String zkPath, Set<Modifier> modifierSet) {
		this.zkPath = zkPath;
		this.modifierSet = modifierSet;
	}

	@Override
	public void stateChanged(CuratorFramework client, ConnectionState newState) {
		LOGGER.trace("state has changed to " + newState);
		
		switch (newState) {
		case CONNECTED:
		case LOST:
			for (Modifier modifier : modifierSet) {
				if (modifier instanceof FieldEditor) {
					((FieldEditor) modifier).set("false");
				} else if (modifier instanceof MethodInvoker) {
					((MethodInvoker) modifier).invoke("false");
				}
			}

			break;

		default:
			break;
		}
	}

	@Override
	public void takeLeadership(CuratorFramework client) throws Exception {
		LOGGER.trace("take leader ship(" + zkPath + ")");
		
		for (Modifier modifier : modifierSet) {
			if (modifier instanceof FieldEditor) {
				((FieldEditor) modifier).set("true");
			} else if (modifier instanceof MethodInvoker) {
				((MethodInvoker) modifier).invoke("true");
			}
		}

		synchronized (lock) {
			lock.wait();
		}

		LOGGER.info("release leader ship(" + zkPath + ")");
	}
}
