package tgx.gradle

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

// XGram: a shared build service with no state, registered with maxParallelUsages = 1 and used by the
// buildCMake* tasks (app/build.gradle.kts), so only one ABI's native build (and its full-LTO link of
// libtgcallsjni.so, ~4 GB) runs at a time. With the configuration cache Gradle otherwise runs both ABIs' builds
// in parallel and the 12 GB build server went into swap. A build of a single ABI is not affected.
abstract class NativeBuildLimiter : BuildService<BuildServiceParameters.None>
