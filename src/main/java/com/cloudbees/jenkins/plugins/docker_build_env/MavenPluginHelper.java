package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.Extension;
import hudson.maven.TcpSocketHostLocator;

import java.io.IOException;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension(ordinal = 99)
public class MavenPluginHelper extends TcpSocketHostLocator {

    @Override
    public String getTcpSocketHost() throws IOException {
        return "dockerhost";
    }
}
