package org.sunix.jhpages;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.kohsuke.github.GHCreateRepositoryBuilder;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.okhttp3.OkHttpConnector;

import okhttp3.OkHttpClient;

@ApplicationScoped
public class GitHubService {

    private String repoName;
    private GitHub gitHub;
    private String repoOrgName;
    private File tempWorkingDir = createTempDir();
    private UsernamePasswordCredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(
            System.getenv("GITHUB_LOGIN"), System.getenv("GITHUB_PASSWORD"));
    private Git git;

    public void init() {
        try {
            this.gitHub = GitHubBuilder //
                    .fromEnvironment() //
                    .withConnector(new OkHttpConnector(new OkHttpClient())) //
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private File createTempDir() {
        File file;
        try {
            file = File.createTempFile("jh-pages-", "");
        } catch (IOException e) {
            throw new RuntimeException("an error occured while creating a temp dir", e);
        }
        file.mkdir();
        return file;
    }

    public boolean checkRepoExist() {
        try {
            return gitHub.getRepository(getFullRepoName()) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public String getRepoName() {
        return repoName;
    }

    public boolean checkGhPagesBranchExist() {
        try {
            return gitHub.getRepository(getFullRepoName()).getBranch("gh-pages") != null;
        } catch (Exception e) {
            return false;
        }
    }

    public GitHubService withFullRepoName(String fullRepoName) {
        String[] tokens = fullRepoName.split("/");
        this.repoOrgName = tokens[0];
        this.repoName = tokens[1];
        return this;
    }

    public void createRepo() {
        try {
            new GHCreateRepositoryBuilder(this.repoName, gitHub, "/user/repos") //
                    .autoInit(true) //
                    .defaultBranch("gh-pages") //
                    .create();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getFullRepoName() {
        return this.repoOrgName + "/" + this.repoName;
    }

    public void createBranch(String branchName, File folder) {

        Git git = getOrCloneGitProject();

        // only to be used if no gh-pages branch
        try {

            // remove all the content
            deleteGitRepoContent(tempWorkingDir);
            // copy a directory content to be pushed
            Arrays.stream( //
                    folder.listFiles()) //
                    .forEach(file -> {
                        try {
                            System.out.println("trying to copy " + file.getAbsolutePath() + " to "
                                    + tempWorkingDir.getAbsolutePath());
                            copyDirectory(file.getAbsolutePath(),
                                    tempWorkingDir.getAbsolutePath() + "/" + file.getName());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
            // git add .
            git.add().addFilepattern(".") //
                    .call();
            git.add().addFilepattern(".") //
                    .setUpdate(true)//
                    .call();
            git.commit()//
                    .setMessage("from jh-pages ...")//
                    .call();
            git.push() //
                    .setCredentialsProvider(credentialsProvider) //
                    .call();
        } catch (Exception e) {
            throw new RuntimeException("An error occured while ....", e);
        }

    }

    public Git getOrCloneGitProject() {
        if (git != null) {
            return git;
        }
        try {
            deleteRecursif(tempWorkingDir);
            UsernamePasswordCredentialsProvider credentialsProvider = getCredentialsProvider();
            git = Git.cloneRepository() //
                    .setURI(getRepoURL()) //
                    .setCredentialsProvider(credentialsProvider) //
                    .setDirectory(tempWorkingDir) //
                    .call();
            return git;
        } catch (Exception e) {
            throw new RuntimeException("An error occured while trying cloning the repo " + getRepoURL(), e);
        }
    }

    private UsernamePasswordCredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }

    public void copyContentAndPush(File folder) {
        Git git = getOrCloneGitProject();

        try {

            // only to be used if no gh-pages branch

            // remove all the content
            deleteGitRepoContent(tempWorkingDir);
            // copy a directory content to be pushed
            Arrays.stream( //
                    folder.listFiles()) //
                    .forEach(file -> {
                        try {
                            System.out.println("trying to copy " + file.getAbsolutePath() + " to "
                                    + tempWorkingDir.getAbsolutePath());
                            copyDirectory(file.getAbsolutePath(),
                                    tempWorkingDir.getAbsolutePath() + "/" + file.getName());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
            // git add .
            git.add().addFilepattern(".") //
                    .call();
            git.add().addFilepattern(".") //
                    .setUpdate(true)//
                    .call();
            git.commit()//
                    .setMessage("from jh-pages ...")//
                    .call();
            git.push() //
                    .setCredentialsProvider(credentialsProvider) //
                    .call();

        } catch (Exception e) {
            throw new RuntimeException("An error occured while trying do something in the repo gh-pages", e);
        }

    }

    public static void copyDirectory(String sourceDirectoryLocation, String destinationDirectoryLocation)
            throws IOException {
        Files.walk(Paths.get(sourceDirectoryLocation)).forEach(source -> {
            Path destination = Paths.get(destinationDirectoryLocation,
                    source.toString().substring(sourceDirectoryLocation.length()));
            try {
                Files.copy(source, destination);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    boolean deleteRecursif(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteRecursif(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    void deleteGitRepoContent(File gitRepoDirToBeDeleted) {
        Arrays.stream( //
                gitRepoDirToBeDeleted.listFiles()) //
                .filter(file -> !file.getName().equals(".git")) //
                .forEach(file -> deleteRecursif(file));
    }

    public String getRepoURL() {
        return "https://github.com/" + getFullRepoName();
    }

    public void checkoutGhPagesBranch() {
        try {
            getOrCloneGitProject().checkout() //
                    .setCreateBranch(true) //
                    .setStartPoint("origin/gh-pages") //
                    .setName("gh-pages") //
                    .call();
        } catch (Exception e) {
            throw new RuntimeException("An error occured while trying do something in the repo gh-pages", e);
        }
    }

    public void checkoutOrphanGhPagesBranch() {
        try {
            git.checkout().setOrphan(true).setName("gh-pages").call();
        } catch (Exception e) {
            throw new RuntimeException("An error occured while trying do something in the repo gh-pages", e);
        }
    }

}
