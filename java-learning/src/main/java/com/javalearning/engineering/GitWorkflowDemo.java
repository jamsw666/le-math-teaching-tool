package com.javalearning.engineering;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 9-2 Git 协作与分支策略。
 *
 * <p>手写 MiniGit 理解 Git 的对象模型：blob / tree / commit 都是内容寻址（SHA-1）的不可变对象，
 * 分支只是指向 commit 的可变指针。演示 commit、分支、fast-forward 与三方合并、冲突检测、
 * 以及 rebase 为什么会"改写历史"。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.engineering.GitWorkflowDemo
 */
public class GitWorkflowDemo {

    record Commit(String id, String parent, String secondParent, Map<String, String> tree, String message) { }

    static class MiniGit {
        /** 对象库：SHA-1 -> 内容。真实 Git 存在 .git/objects，并用 zlib 压缩。 */
        final Map<String, String> blobs = new HashMap<>();
        final Map<String, Commit> commits = new LinkedHashMap<>();
        /** 分支 = 指向 commit 的指针（.git/refs/heads/xxx 文件里就一行 SHA）。 */
        final Map<String, String> branches = new LinkedHashMap<>();
        String head = "main";
        /** 工作区（简化为 文件名 -> 内容）。 */
        final Map<String, String> workTree = new TreeMap<>();

        String commit(String message) {
            Map<String, String> tree = new TreeMap<>();
            workTree.forEach((file, content) -> {
                String blobId = sha1("blob " + content.length() + "\0" + content);
                blobs.put(blobId, content); // 内容相同的文件只存一份
                tree.put(file, blobId);
            });
            String parent = branches.get(head);
            String id = sha1("commit " + tree + parent + message);
            commits.put(id, new Commit(id, parent, null, tree, message));
            branches.put(head, id);
            return id;
        }

        void branch(String name) { branches.put(name, branches.get(head)); }

        void checkout(String name) {
            head = name;
            workTree.clear();
            Commit c = commits.get(branches.get(name));
            if (c != null) c.tree().forEach((f, b) -> workTree.put(f, blobs.get(b)));
        }

        /** 合并 other 到当前分支。 */
        String merge(String other) {
            String ours = branches.get(head);
            String theirs = branches.get(other);
            if (isAncestor(ours, theirs)) {
                branches.put(head, theirs);
                checkout(head);
                return "fast-forward：当前分支没有新提交，直接把指针移到 " + short7(theirs);
            }
            String base = mergeBase(ours, theirs);
            Map<String, String> result = new TreeMap<>();
            List<String> conflicts = new ArrayList<>();
            Map<String, String> bT = commits.get(base).tree();
            Map<String, String> oT = commits.get(ours).tree();
            Map<String, String> tT = commits.get(theirs).tree();
            var files = new java.util.TreeSet<>(bT.keySet());
            files.addAll(oT.keySet());
            files.addAll(tT.keySet());
            for (String f : files) {
                String b = bT.get(f);
                String o = oT.get(f);
                String t = tT.get(f);
                if (java.util.Objects.equals(o, t)) {
                    if (o != null) result.put(f, o); // 双方相同（含都删除）
                } else if (java.util.Objects.equals(b, o)) {
                    if (t != null) result.put(f, t); // 只有对方改了
                } else if (java.util.Objects.equals(b, t)) {
                    if (o != null) result.put(f, o); // 只有我方改了
                } else {
                    conflicts.add(f); // 双方都改了同一文件且不同 -> 冲突（真实 Git 还会按行/块比较）
                    result.put(f, o);
                }
            }
            if (!conflicts.isEmpty()) {
                return "CONFLICT：" + conflicts + " 需要手工解决后 git add + git commit";
            }
            workTree.clear();
            result.forEach((f, b) -> workTree.put(f, blobs.get(b)));
            Map<String, String> tree = new TreeMap<>(result);
            String id = sha1("merge " + tree + ours + theirs);
            commits.put(id, new Commit(id, ours, theirs, tree, "Merge " + other + " into " + head));
            branches.put(head, id);
            return "三方合并（base=" + short7(base) + "）生成合并提交 " + short7(id) + "，有两个父提交";
        }

        boolean isAncestor(String maybeAncestor, String of) {
            for (String c = of; c != null; c = commits.get(c).parent()) {
                if (c.equals(maybeAncestor)) return true;
            }
            return false;
        }

        String mergeBase(String a, String b) {
            var ancestorsA = new java.util.HashSet<String>();
            for (String c = a; c != null; c = commits.get(c).parent()) ancestorsA.add(c);
            for (String c = b; c != null; c = commits.get(c).parent()) {
                if (ancestorsA.contains(c)) return c;
            }
            return null;
        }

        void log() {
            String c = branches.get(head);
            while (c != null) {
                Commit cm = commits.get(c);
                String refs = branches.entrySet().stream().filter(e -> e.getValue().equals(cm.id())).map(Map.Entry::getKey).toList().toString();
                System.out.printf("    %s %s %s%n", short7(cm.id()), refs.equals("[]") ? "" : refs, cm.message());
                c = cm.parent();
            }
        }
    }

    public static void main(String[] args) {
        MiniGit git = new MiniGit();
        objectModel(git);
        branchingAndFastForward(git);
        threeWayMerge(git);
        conflict(git);
        rebaseExplained();
        printStrategies();
    }

    static void objectModel(MiniGit git) {
        System.out.println("== 对象模型：内容寻址 ==");
        git.workTree.put("README.md", "# demo");
        git.workTree.put("app.java", "class App {}");
        String c1 = git.commit("init");
        git.workTree.put("app.java", "class App { int v = 1; }");
        String c2 = git.commit("add field");
        System.out.println("  提交 1: " + short7(c1) + "  提交 2: " + short7(c2) + "  parent(2)=" + short7(git.commits.get(c2).parent()));
        System.out.println("  blob 对象数: " + git.blobs.size() + "（README 未变，两个提交共享同一个 blob；Git 的快照不是全量拷贝）");
        System.out.println("  SHA-1 由内容决定 -> 任何历史被篡改，后续所有提交的 id 都会变化，这就是 Git 的完整性保证");
        System.out.println("  三棵树：工作区(Working Tree) -> git add -> 暂存区(Index) -> git commit -> 版本库(HEAD)");
    }

    static void branchingAndFastForward(MiniGit git) {
        System.out.println("\n== 分支与 fast-forward ==");
        git.branch("feature/login");
        git.checkout("feature/login");
        git.workTree.put("login.java", "class Login {}");
        git.commit("feat: login page");
        System.out.println("  在 feature/login 提交后，main 仍指向旧提交。分支切换 = 改 HEAD 指针 + 恢复工作区，所以极快极便宜");
        git.checkout("main");
        System.out.println("  git merge feature/login -> " + git.merge("feature/login"));
        System.out.println("  --no-ff 可以强制生成合并提交，保留“这是一个功能分支”的历史形状");
    }

    static void threeWayMerge(MiniGit git) {
        System.out.println("\n== 三方合并 ==");
        git.branch("feature/pay");
        git.checkout("feature/pay");
        git.workTree.put("pay.java", "class Pay {}");
        git.commit("feat: pay");
        git.checkout("main");
        git.workTree.put("README.md", "# demo\n\nhotfix note");
        git.commit("docs: hotfix note");
        System.out.println("  main 与 feature/pay 各自有新提交 -> " + git.merge("feature/pay"));
        System.out.println("  合并后工作区文件: " + git.workTree.keySet());
        System.out.println("  git log --graph:");
        git.log();
    }

    static void conflict(MiniGit git) {
        System.out.println("\n== 冲突 ==");
        git.branch("feature/rename");
        git.checkout("feature/rename");
        git.workTree.put("app.java", "class Application { int v = 1; }");
        git.commit("refactor: rename");
        git.checkout("main");
        git.workTree.put("app.java", "class App { int v = 2; }");
        git.commit("fix: v=2");
        System.out.println("  双方都改了 app.java -> " + git.merge("feature/rename"));
        System.out.println("  真实 Git 会在文件中插入 <<<<<<< HEAD / ======= / >>>>>>> 标记；解决后 git add 标记已解决。放弃用 git merge --abort");
    }

    static void rebaseExplained() {
        System.out.println("\n== rebase vs merge ==");
        System.out.println("""
                  merge:  保留真实历史，产生合并提交，图形呈“菱形”
                          A---B---C main
                               \\   \\
                                D---E---M  (M 有两个父)
                  rebase: 把 feature 的提交“摘下来”逐个重放到 main 顶端，生成新 id 的 D' E'，历史线性
                          A---B---C main
                                   \\
                                    D'--E'  feature
                  rebase 后提交 id 全变（内容寻址），所以：绝不 rebase 已推送且他人基于其开发的分支（黄金法则）
                  推荐：个人分支用 rebase 整理再合并，公共分支只 merge；git pull --rebase 避免无意义的 merge 提交
                  git rebase -i 交互式：squash 合并碎提交、reword 改信息、drop 删除；git reflog 是后悔药，能找回被“改写”掉的提交""");
    }

    static void printStrategies() {
        System.out.println("\n== 分支策略 ==");
        System.out.println("""
                  Git Flow         master / develop / feature/* / release/* / hotfix/*，适合有明确版本发布周期的产品；分支多、流程重
                  GitHub Flow      只有 main + 短生命周期 feature 分支，PR + Code Review + CI 通过即合并并部署；适合持续交付的 Web 服务
                  GitLab Flow      GitHub Flow + 环境分支（pre-production / production）或版本分支，兼顾持续部署与多环境
                  Trunk-Based      所有人直接向 trunk 小步提交（或极短分支 < 1 天），用 Feature Toggle 隐藏未完成功能；对测试与 CI 要求最高
                提交规范（Conventional Commits）：feat: / fix: / docs: / refactor: / test: / chore:  + 可选 scope + 正文 + BREAKING CHANGE；
                  配合 commitlint 校验、standard-version / semantic-release 自动生成 CHANGELOG 与版本号
                常用操作速查：
                  git stash 临时搁置 | git cherry-pick <sha> 挑单个提交 | git bisect 二分定位引入 bug 的提交
                  git reset --soft/--mixed/--hard 回退（分别保留暂存/工作区/全删） | git revert 生成反向提交（公共分支用这个）
                  git blame -L 10,20 file 看每行作者 | git log -S "keyword" 搜索改动内容 | .gitignore 与 .gitattributes（换行符、LFS）
                参考：Pro Git（git-scm.com/book/zh）、conventionalcommits.org、trunkbaseddevelopment.com
                """);
    }

    static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String short7(String id) { return id == null ? "null" : id.substring(0, 7); }
}
