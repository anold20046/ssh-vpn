package com.sshvpn;

public interface ProxyManager {
    void enableProxy(String host, int port);
    void disableProxy();
}