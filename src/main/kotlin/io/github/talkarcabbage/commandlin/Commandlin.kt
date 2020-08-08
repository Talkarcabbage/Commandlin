package io.github.talkarcabbage.commandlin

enum class CommandResult {
    SUCCESS,
    INSUFFICIENT_PRIVILEGES,
    NO_MATCHING_COMMAND,
    INVALID_ARGUMENTS,
    NO_COMMAND_FUNCTION
}



/**
 * S: The type representing the source of the command. Can be any type. Passed to command when executed.
 * P: The type used by the PermissionVerifier implementation used. The built-in type,
 * BasicPermissionVerifier, uses Int as its type.
 * If you do not need a source and/or permission type, passing the Nothing type should work. Most uses of
 * these generics are nullable, as well.
 *
 */
fun <S, P> commandlin(incFun: CommandlinManager<S, P>.() -> Any): CommandlinManager<S, P> {
    val commandlinBuilder = CommandlinManager<S, P>()
    commandlinBuilder.incFun()
    return commandlinBuilder
}

class CommandlinManager<S, P> {
    val commands: MutableList<Command<S, P>> = mutableListOf()
    /**
     * If true, a command must have a permissions assigned during creation, or it will not be added.
     * It cannot be disabled once set to enabled!
     */
    var requireCommandPermission=false
        set(enabled) {
            if (enabled)
                field = enabled
        }

    private fun addCommand(com: Command<S, P>) = commands.add(com)
    /**
     * Process the given command, with arguments given as an Array of strings.
     * Execution flows as follows.
     *
     * 1. If a command is not matched, CommandResult.NO_MATCHING_COMMAND is returned.
     *
     * 2. IF the command has an attached PermissionVerifier, it will be passed P. If the PermissionVerifier returns false,
     * the command function will not execute and CommandResult.INSUFFICIENT_PRIVILEGES is returned.
     * If NO permissionVerifier was set on the command, this step is skipped entirely.
     *
     * 3. The command will not execute if expected arguments minimum or maximum are set and
     * do not match requirements.
     *
     */
    fun process(commandId: String, args: List<String>, permissionObject: P?=null, source: S?=null): CommandResult {
        val command: Command<S, P>? = findMatchingCommand(commandId)
        if (command != null) {
            val cpv=command.permissionVerifier
            if (!(command.permissionVerifier==null || (cpv!=null && permissionObject!=null && cpv.checkPermission(command, permissionObject)))) {
                return CommandResult.INSUFFICIENT_PRIVILEGES
            }
            if (!command.verifyArgsCount(args.size)) {
                return CommandResult.INVALID_ARGUMENTS
            }
            val cmd = command.commandFunction
            return if (cmd!=null) {
                val result: Any? = cmd(args, source)
                if (result is CommandResult) result else CommandResult.SUCCESS
            } else {
                CommandResult.NO_COMMAND_FUNCTION
            }
        } else {
            return CommandResult.NO_MATCHING_COMMAND
        }
    }
    /**
     * Process the given command, with arguments given as a single string.
     * The command will not execute if expected arguments are set and
     * do not match requirements.
     * Convenience method. Splits the arguments by spaces, passing them to process(String, List<String>, P, S)
     */
    fun process(commandId: String, arg: String, permissionObject: P?=null, source: S?=null): CommandResult {
        println("Processing middle tier with $arg")
        return if (arg.trim()=="") {
            println("Processing args==\"\"")
            process(commandId,listOf(), permissionObject, source)
        } else {
            println("Processing args!=\"\" with split results size of ${arg.split(' ').size}")
            process(commandId, arg.split(' '), permissionObject, source)
        }
    }
    /**
     * Process the given command, with arguments given as a single string.
     * The command will not execute if expected arguments are set and
     * do not match requirements.
     * Convenience method. First argument is treated as commandId.
     * Splits the remaining arguments by space and passes them to process(String, String, P, S)
     */
    fun process(input: String, permissionObject: P?=null, source: S?=null): CommandResult {
        println("Processed command as [${input.substringBefore(' ')}] and args as [${input.substringAfter(' ', "")}]")
        return process(input.substringBefore(' '), input.substringAfter(' ', ""), permissionObject, source)
    }

    fun findMatchingCommand(commandId: String): Command<S, P>? {
        for (cmd in commands) {
            if (cmd.name==commandId || cmd.aliases.contains(commandId)) {
                return cmd
            }
        }
        return null
    }

    /**
     * Add a command to the command manager.
     * The commandlin instance should ideally be configured prior to calling this function.
     * The command may be configured from within this function.
     * This function will throw an IllegalStateException if requireCommandPermission is set and
     * the command does not have a permission verifier set in its lambda.
     */
    fun command(id: String, incFun: Command<S, P>.() -> Any) {
        if (findMatchingCommand(id)!=null) {
            println("Failed to add a command that already exists or has an alias: $id")
            return
        }
        val newCmd = Command<S, P>(id)
        newCmd.incFun()
        if (requireCommandPermission && newCmd.permissionVerifier==null) {
            throw IllegalStateException("Attempted to add a command with no permission verifier: ${newCmd.name}")
        }
        if (newCmd.commandFunction==null) {
            println("The following command was added with no command function: ${newCmd.name}")
        }
        addCommand(newCmd)
    }
}

class Command<S, P>(var name: String) {
    /**
     * Set the expected minimum argument count. The command will fail if
     * this is set to a non-negative value and the number of arguments does not
     * exceed this value.
     */
    var expectedArgsMin = -1
    /**
     * Set the expected maximum argument count. The command will fail if
     * this is set to a non-negative value and the number of arguments does not
     * fall under or equal to this value.
     */
    var expectedArgsMax = -1
    /**
     * The permissions verifier object for this command. If it is non-null, this
     * verifier will be called with the given P object and must return true for the command to execute.
     */
    var permissionVerifier: PermissionVerifier<P>? = null
    /**
     * The actual function/lambda to execute
     */
    var commandFunction: ((List<String>, S?) -> Any)? = null
    /**
     * Optional map for storing instanced data for the command without a need for subclassing.
     * Note that this data is unique to the Command object, not to each individual call.
     * Properties object is initialized lazily using kotlin's lazy delegate.
     */
    val properties: MutableMap<String, String> by lazy { HashMap<String, String>() }
    /**
     * Alternative command names that will also activate this command.
     * Matching-wise they function the same as the base name of the command.
     * Initialized lazily using kotlin's lazy delegate.
     */
    val aliases: MutableList<String> by lazy {mutableListOf<String>()}

    fun verifyArgsCount(count: Int): Boolean {
        if (expectedArgsMin<0 && expectedArgsMax<0) return true
        if (expectedArgsMin>count) return false
        if (expectedArgsMax in 0 until count) return false //If argsmax is above -1 and above count then fail
        return true
    }

    fun alias(name: String) {
        aliases.add(name)
    }

    /**
     * The function of the command. Note that at this point
     * we are assuming we have permission to run the command,
     * as the permission verifier should be called and tested for
     * a true boolean prior to this function being executed.
     */
    fun func(incFunction: ((List<String>, S?) -> Any)) {
        this.commandFunction = incFunction
    }
}

/**
 * An interface for defining a PermissionVerifier, which can be used
 * to check whether a command is allowed to execute given a specified
 * object to verify. The provided implementation is [BasicPermissionVerifier]
 */
interface PermissionVerifier<T> {
    fun checkPermission(command: Command<*, T>, authToken: T): Boolean
}

/**
 * A simple permission verifier for commands. It returns true if the value
 * provided is higher than or equal to the number it is instantiated with.
 */
class BasicPermissionVerifier(var requiredLevel: Int): PermissionVerifier<Int>  {
    override fun checkPermission(command: Command<*, Int>, authToken: Int): Boolean {
        return (authToken>=this.requiredLevel)
    }
}