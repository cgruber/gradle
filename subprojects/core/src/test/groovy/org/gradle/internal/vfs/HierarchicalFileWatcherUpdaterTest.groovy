/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.vfs

import net.rubygrapefruit.platform.file.FileWatcher
import org.gradle.internal.vfs.watch.FileWatcherUpdater

class HierarchicalFileWatcherUpdaterTest extends AbstractFileWatcherUpdaterTest {

    @Override
    FileWatcherUpdater createUpdater(FileWatcher watcher) {
        new HierarchicalFileWatcherUpdater(watcher)
    }

    def "only adds watches for the roots of must watch directories"() {
        def mustWatchDirectoryRoots = ["first", "second"].collect { file(it).createDir() }
        def mustWatchDirectories = mustWatchDirectoryRoots + file("first/within").createDir()

        when:
        updater.updateMustWatchDirectories(mustWatchDirectories)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, mustWatchDirectoryRoots) })
        0 * _

        when:
        updater.updateMustWatchDirectories([file("first/within"), file("second")])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [file("first")]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [file("first/within")]) })
        0 * _

        when:
        updater.updateMustWatchDirectories(mustWatchDirectoryRoots)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [file("first/within")]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [file("first")]) })
        0 * _
    }

    def "does not watch snapshot roots in must watch directories"() {
        def rootDir = file("root").createDir()
        updater.updateMustWatchDirectories([rootDir])
        def subDirInRootDir = rootDir.file("some/path").createDir()
        def snapshotInRootDir = snapshotDirectory(subDirInRootDir)

        when:
        addSnapshot(snapshotInRootDir)
        then:
        0 * _

        when:
        updater.updateMustWatchDirectories([])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [rootDir]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, ([subDirInRootDir.parentFile])) })
        0 * _
    }

    def "watchers are stopped when removing the last watched snapshot"() {
        def rootDir = file("root").createDir()
        ["first", "second", "third"].collect { rootDir.createFile(it) }
        def rootDirSnapshot = snapshotDirectory(rootDir)

        when:
        addSnapshot(rootDirSnapshot)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [rootDir.parentFile]) })
        0 * _

        when:
        invalidate(rootDirSnapshot.children[0])
        invalidate(rootDirSnapshot.children[1])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [rootDir.parentFile]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [rootDir]) })
        0 * _

        when:
        invalidate(rootDirSnapshot.children[2])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [rootDir]) })
        0 * _
    }
}
