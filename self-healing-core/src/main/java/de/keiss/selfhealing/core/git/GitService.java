package de.keiss.selfhealing.core.git;

import de.keiss.selfhealing.core.config.SelfHealingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Encapsulates JGit operations for the auto-fix PR workflow: branch creation, file update, commit, and push.
 */
@Slf4j
@RequiredArgsConstructor
public class GitService {

    private static final DateTimeFormatter BRANCH_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path repoPath;
    private final SelfHealingProperties.GitPr config;

    /**
     * Creates a fix branch, writes the healed source, commits, and pushes.
     *
     * @param pageObjectClassName
     *            simple class name (e.g. "SearchPage")
     * @param packageFilePath
     *            package-relative path (e.g. "de/keiss/.../SearchPage.java")
     * @param fixedSource
     *            the complete healed Page Object source code
     * @param commitMessage
     *            structured commit message
     * @return the branch name that was pushed
     */
    public String commitAndPush(String pageObjectClassName, String packageFilePath, String fixedSource,
            String commitMessage) throws IOException, GitAPIException {
        String branchName = buildBranchName(pageObjectClassName);

        if (config.dryRun()) {
            String repoRelativePath = resolveRepoRelativePath(packageFilePath);
            log.info("[DRY-RUN] Would create branch '{}' from '{}' and commit {} ({} chars) with message:\n{}",
                    branchName, config.baseBranch(), repoRelativePath, fixedSource.length(), commitMessage);
            return branchName;
        }

        try (Git git = Git.open(repoPath.toFile())) {
            // Ensure we start from the latest base branch state
            git.checkout().setName(config.baseBranch()).call();

            // Create and checkout fix branch from the local base branch.
            // Using the local ref (not origin/<branch>) avoids issues when
            // tracking refs are out of date or the repo was cloned shallow.
            git.checkout().setCreateBranch(true).setName(branchName).setStartPoint(config.baseBranch()).call();

            // Resolve the full repo-relative path (e.g. src/test/java/de/keiss/SearchPage.java)
            String repoRelativePath = resolveRepoRelativePath(packageFilePath);

            // Write the fixed source file
            Path targetFile = repoPath.resolve(repoRelativePath);
            Files.createDirectories(targetFile.getParent());
            Files.writeString(targetFile, fixedSource, StandardCharsets.UTF_8);

            // Stage and commit — git add needs the repo-relative path
            git.add().addFilepattern(repoRelativePath).call();
            git.commit().setMessage(commitMessage).call();

            // Push to remote
            git.push().setRemote(config.remoteName()).setCredentialsProvider(credentialsProvider()).call();

            log.info("Pushed fix branch '{}' with healed {}", branchName, pageObjectClassName);

            // Switch back to the original branch so test execution continues on the working tree
            git.checkout().setName(config.baseBranch()).call();
        }

        return branchName;
    }

    String buildBranchName(String pageObjectClassName) {
        String timestamp = LocalDateTime.now().format(BRANCH_TIMESTAMP);
        return config.branchPrefix() + pageObjectClassName + "-" + timestamp;
    }

    /**
     * Resolves a package-relative file path (e.g. "de/keiss/SearchPage.java") to a repo-relative path (e.g.
     * "integration-tests/src/test/java/de/keiss/SearchPage.java") by probing common source roots, including one level
     * of submodule directories for multi-module projects.
     */
    String resolveRepoRelativePath(String packageFilePath) {
        for (String srcRoot : candidateSourceRoots()) {
            Path candidate = repoPath.resolve(srcRoot).resolve(packageFilePath);
            if (Files.exists(candidate)) {
                return srcRoot + packageFilePath;
            }
        }
        // Fall back to test sources at repo root
        return "src/test/java/" + packageFilePath;
    }

    private String[] candidateSourceRoots() {
        String[] standard = {"src/test/java/", "src/main/java/"};
        try {
            var submodulePrefixes = Files.list(repoPath)
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> !name.startsWith(".") && !name.equals("build"))
                    .flatMap(sub -> java.util.Arrays.stream(standard).map(root -> sub + "/" + root))
                    .toArray(String[]::new);

            String[] all = new String[standard.length + submodulePrefixes.length];
            System.arraycopy(standard, 0, all, 0, standard.length);
            System.arraycopy(submodulePrefixes, 0, all, standard.length, submodulePrefixes.length);
            return all;
        } catch (IOException e) {
            log.debug("Could not list submodules of {}: {}", repoPath, e.getMessage());
            return standard;
        }
    }

    private CredentialsProvider credentialsProvider() {
        return new UsernamePasswordCredentialsProvider(config.githubToken(), "");
    }
}
