
# Commandlin

Commandlin is a small, simple, and convenient command management library for Kotlin that manages the handling and delegation of "commands". Example use cases are things such as commandline argument handling, terminal command feedback, bot commands, etc.

It makes extensive use of generics, having such functionality for a user-controllable permission system, a custom Arguments type, a Source type to note the origin, destination, (or etc.!) of a command execution, and Properties to allow attaching custom data to an individual command object (per COMMAND, not per individual execution).

Kotlin DSL is used to simplify the building and usage of the commands.
The commandlin{} function is the typical way to use all features, while simpleCommandlin{} can be used to prefill the generics such that arguments are a List<String>? and other generics are the Nothing? type.

The compiler may get confusingly angry at you if your call to command{} does not yet contain a func{}/etc. or end with an explicit return, 
due to how the incoming function and its return types are implemented. Be sure to add the command's function!

It is possible to return a CommandResult from the func{} functions, but it generally needs to be written as
`return@func ...` rather than a plain return statement.

## Usage/Examples

```kotlin
// A short example showing off most features
// Arguments are provided as a list of Strings
// Permissions data is passed using an Int
// The Source object is an Author class provided
// Each command can have its own Props object.
fun main() {
    var readmeCMD: CommandlinManager<List<String>, Int, Author?, Props>? = null
    readmeCMD = commandlin {
        requireCommandPermission = true
        commandRegistrar = ExternalRegistryHelper()
        command("test") {
            properties = Props("This is a test command!")
            permissionVerifier = BasicPermissionVerifier(1) //Built-in simple integer permission system - permission value in .process() must be equal or greater than this value to execute
            func { args, source ->
                println("We have ${args.size} arguments from ${source?.name}: $args")
            }
        }
        command("help") {
            properties = Props("Print this help statement.")
            permissionVerifier = BasicPermissionVerifier(0)
            func { args, source ->
                readmeCMD?.commands?.forEach { (mapKey, command) ->
                    println("${command.id} - ${command.properties?.commandHelpText}")
                }
            }
        }
    }

    val someArguments = listOf("Arg one", "Arg two")
    val someOtherArguments = listOf("This is a test")
    val author = Author("Steve")

    val failedResult = readmeCMD.process("test", listOf("This should not print"), 0, author)
    println(failedResult)
    val successResult = readmeCMD.process("test", someArguments, 1, author)
    println(successResult)
    readmeCMD.process("test", someOtherArguments, 1, null)
    readmeCMD.process("help", someArguments, 1, null)
}

class Author(var name: String)
class Props(var commandHelpText: String)

class ExternalRegistryHelper: CommandRegistrar<List<String>, Int, Author?, Props> {
    override fun handleRegistration(command: Command<List<String>, Int, Author?, Props>) {
        // exampleExternalTool.addCommand(command.id, otherParameters)
        println("We added a command! ${command.id}")
    }
    override fun handleDeregistration(command: Command<List<String>, Int, Author?, Props>) {
        // exampleExternalTool.removeCommand(command.id)
        println("We removed a command! ${command.id}")
    }
}
```

```kotlin
//A simplified version using simpleCommandlin.
fun main() {
    val cmd = simpleCommandlin {
        command("hello") {
            funcNP {
                println(" world!")
            }
        }
        command("thiscommandfails") {
            funcNP {
                return@funcNP CommandResult.GENERIC_FAILURE
            }
        }
    }
    cmd.process("hello", null,null,null)
}
```