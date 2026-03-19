package dsm

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces console output only")
abstract class DsmAgentHelpTask : DefaultTask() {

    @TaskAction
    fun showAgentHelp() {
        logger.lifecycle(DsmAgentHelpText.generate())
    }
}
