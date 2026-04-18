package de.keiss.selfhealing.core.git;

import de.keiss.selfhealing.core.config.SelfHealingProperties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests GitService against a real (local) JGit repository in a temp directory. No remote push — we verify branch
 * creation, file writing, and commit content.
 */
class GitServiceTest {

    @TempDir
    Path tempDir;

    private Path repoDir;
    private SelfHealingProperties.GitPr config;

    @BeforeEach
    void setUp() throws GitAPIException, IOException {
        // Create a bare "remote" and a local clone to simulate the real workflow.
        // Explicitly set "main" as initial branch — JGit defaults to "master".
        Path remoteDir = tempDir.resolve("remote.git");
        Git.init().setDirectory(remoteDir.toFile()).setBare(true).setInitialBranch("main").call().close();

        repoDir = tempDir.resolve("local");

        // Initialize local repo manually (instead of clone) to control the branch name.
        // JGit's clone from an empty bare repo may default to "master".
        try (Git git = Git.init().setDirectory(repoDir.toFile()).setInitialBranch("main").call()) {
            // Disable GPG signing — JGit does not support the system git signing
            // format (e.g. ssh) and would throw UnsupportedSigningFormatException.
            var repoConfig = git.getRepository().getConfig();
            repoConfig.setBoolean("commit", null, "gpgSign", false);
            repoConfig.setString("remote", "origin", "url", remoteDir.toUri().toString());
            repoConfig.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
            repoConfig.save();

            // Create initial commit on main so the branch base exists
            Path readme = repoDir.resolve("README.md");
            Files.writeString(readme, "# Test Repo", StandardCharsets.UTF_8);
            git.add().addFilepattern("README.md").call();
            git.commit().setMessage("Initial commit").call();
            git.push().setRemote("origin").call();
        }

        config = new SelfHealingProperties.GitPr(true, false, "origin", "main", "fix/self-healing-", "dummy-token",
                "test-owner", "test-repo");
    }

    @Test
    void commitAndPush_createsBranchWithFixedSource() throws IOException, GitAPIException {
        var gitService = new GitService(repoDir, config);
        String packagePath = "de/keiss/SearchPage.java";
        String fixedSource = "package de.keiss;\npublic class SearchPage { /* fixed */ }";
        String commitMsg = "fix(self-healing): update SearchPage locators";

        String branchName = gitService.commitAndPush("SearchPage", packagePath, fixedSource, commitMsg);

        assertThat(branchName).startsWith("fix/self-healing-SearchPage-");

        // Verify the commit landed on the fix branch in the remote
        Path remoteDir = tempDir.resolve("remote.git");
        try (Git remoteGit = Git.open(remoteDir.toFile())) {
            var refs = remoteGit.branchList().call();
            assertThat(refs).anyMatch(ref -> ref.getName().endsWith(branchName));

            // Verify commit message
            Iterable<RevCommit> log = remoteGit.log().add(remoteGit.getRepository().resolve("refs/heads/" + branchName))
                    .setMaxCount(1).call();
            RevCommit latest = log.iterator().next();
            assertThat(latest.getFullMessage()).isEqualTo(commitMsg);
        }

        // Verify the local repo is back on main (not the fix branch)
        try (Git localGit = Git.open(repoDir.toFile())) {
            assertThat(localGit.getRepository().getBranch()).isEqualTo("main");
        }
    }

    @Test
    void commitAndPush_writesFileToCorrectLocation() throws IOException, GitAPIException {
        var gitService = new GitService(repoDir, config);
        String packagePath = "de/keiss/SearchPage.java";
        String fixedSource = "package de.keiss;\npublic class SearchPage { /* v2 */ }";

        gitService.commitAndPush("SearchPage", packagePath, fixedSource, "fix: locator update");

        // Even though we switched back to main, the file should exist on the fix branch.
        // Verify by checking out the branch again.
        try (Git git = Git.open(repoDir.toFile())) {
            var refs = git.branchList().call();
            String fixBranch = refs.stream().map(r -> r.getName().replace("refs/heads/", ""))
                    .filter(n -> n.startsWith("fix/self-healing-SearchPage-")).findFirst().orElseThrow();

            git.checkout().setName(fixBranch).call();
            // File is resolved to src/test/java/ (default source root for page objects)
            Path written = repoDir.resolve("src/test/java").resolve(packagePath);
            assertThat(written).exists().hasContent(fixedSource);
        }
    }

    @Test
    void commitAndPush_dryRun_doesNotTouchRepo() throws IOException, GitAPIException {
        var dryRunConfig = new SelfHealingProperties.GitPr(true, true, "origin", "main", "fix/self-healing-",
                "dummy-token", "test-owner", "test-repo");
        var gitService = new GitService(repoDir, dryRunConfig);

        String branchName = gitService.commitAndPush("SearchPage", "de/keiss/SearchPage.java",
                "package de.keiss;\npublic class SearchPage {}", "fix: dry run");

        assertThat(branchName).startsWith("fix/self-healing-SearchPage-");

        // No fix branch should exist locally or on the remote, and no file should be written.
        try (Git localGit = Git.open(repoDir.toFile())) {
            assertThat(localGit.branchList().call()).noneMatch(r -> r.getName().contains("fix/self-healing-"));
            assertThat(localGit.getRepository().getBranch()).isEqualTo("main");
        }
        Path remoteDir = tempDir.resolve("remote.git");
        try (Git remoteGit = Git.open(remoteDir.toFile())) {
            assertThat(remoteGit.branchList().call()).noneMatch(r -> r.getName().contains("fix/self-healing-"));
        }
        assertThat(repoDir.resolve("src/test/java/de/keiss/SearchPage.java")).doesNotExist();
    }

    @Test
    void buildBranchName_containsClassNameAndTimestamp() {
        var gitService = new GitService(repoDir, config);
        String name = gitService.buildBranchName("SearchPage");

        assertThat(name).startsWith("fix/self-healing-SearchPage-")
                .matches("fix/self-healing-SearchPage-\\d{8}-\\d{6}");
    }
}
