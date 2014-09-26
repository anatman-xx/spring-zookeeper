package com.futureseeds.zookeeper.handler;

import java.util.Set;

import com.futureseeds.zookeeper.type.FieldEditor;
import com.github.zkclient.IZkDataListener;

public class ZkDataChangeEventHandler implements IZkDataListener {
	private Set<FieldEditor> fieldEditorSet;

	public ZkDataChangeEventHandler(Set<FieldEditor> fieldEditorSet) {
		this.fieldEditorSet = fieldEditorSet;
	}

	@Override
	public void handleDataDeleted(String dataPath) throws Exception {
	}
	
	@Override
	public void handleDataChange(String dataPath, byte[] data) throws Exception {
		for (FieldEditor fieldEditor : fieldEditorSet) {
			fieldEditor.set(new String(data));
		}
	}
}