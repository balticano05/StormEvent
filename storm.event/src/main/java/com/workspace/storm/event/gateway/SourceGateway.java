package com.workspace.storm.event.gateway;

public interface SourceGateway {
    String sourceId();
    SourceKind kind();
    boolean supportsSuggest();
}
