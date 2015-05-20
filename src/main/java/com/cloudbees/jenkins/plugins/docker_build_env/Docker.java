package com.cloudbees.jenkins.plugins.docker_build_env;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.docker.commons.DockerTool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class Docker {

    private static boolean debug = Boolean.getBoolean(Docker.class.getName()+".debug");
    private final Launcher launcher;
    private final TaskListener listener;
    private final String dockerExecutable;

    public Docker(String dockerInstallation, AbstractBuild build, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        this.dockerExecutable = DockerTool.getExecutable(dockerInstallation, Computer.currentComputer().getNode(), listener, build.getEnvironment(listener));
        this.launcher = launcher;
        this.listener = listener;
    }

    public boolean hasImage(String image) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = launcher.launch()
                .cmds(dockerExecutable, "inspect", image)
                .stdout(out).stderr(err).quiet(!debug).join();
        return status == 0;
    }

    public boolean pullImage(String image) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = launcher.launch()
                .cmds(dockerExecutable, "pull", image)
                .stdout(out).stderr(err).join();
        return status == 0;
    }


    public void buildImage(FilePath workspace, String tag) throws IOException, InterruptedException {

        int status = launcher.launch()
                .pwd(workspace.getRemote())
                .cmds(dockerExecutable, "build", "-t", tag, ".")
                .stdout(listener.getLogger()).stderr(listener.getLogger()).join();
        if (status != 0) {
            throw new RuntimeException("Failed to build docker image from project Dockerfile");
        }
    }

    public void kill(String container) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = launcher.launch()
                .cmds(dockerExecutable, "kill", container)
                .stdout(out).stderr(err).quiet(!debug).join();
        if (status != 0)
            throw new RuntimeException("Failed to stop docker container "+container);

        listener.getLogger().println("Removing Docker container after build completion");
        status = launcher.launch()
                .cmds(dockerExecutable, "rm", container)
                .stdout(out).stderr(err).quiet(!debug).join();
        if (status != 0)
            throw new RuntimeException("Failed to remove docker container "+container);
    }

    public String runDetached(String image, String workdir, Map<String, String> volumes, EnvVars environment, String user, String ... command) throws IOException, InterruptedException {

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(dockerExecutable, "run", "-t", "-d", "-u", user, "-w", workdir);
        for (Map.Entry<String, String> volume : volumes.entrySet()) {
            args.add("-v", volume.getKey() + ":" + volume.getValue() + ":rw" );
        }
        for (Map.Entry<String, String> e : environment.entrySet()) {
            if ("HOSTNAME".equals(e.getKey())) {
                continue;
            }
            args.add("-e");
            args.addMasked(e.getKey()+"="+e.getValue());
        }
        args.add(image).add(command);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int status = launcher.launch()
                .cmds(args).stdout(out).quiet(!debug).stderr(listener.getLogger()).join();

        if (status != 0) {
            throw new RuntimeException("Failed to run docker image");
        }
        return out.toString("UTF-8").trim();
    }
}