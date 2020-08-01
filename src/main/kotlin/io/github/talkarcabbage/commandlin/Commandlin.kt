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
fun <S, P> commandlin(incFun: CommandlinManager<S, P>.() -> Unit): CommandlinManager<S, P> {
    val commandlinBuilder = CommandlinManager<S, P>()
    commandlinBuilder.incFun()
    return commandlinBuilder
}

class CommandlinManager<S, P> {
    val commands: MutableList<Command<S, P>> = mutableListOf()
    var requireCommandPermission=false //If true, a command must have permissions when added.
    private fun addCommand(com: Command<S, P>) = commands.add(com)
    /**
     * Process the given command, with arguments given as an Array of strings.
     * The command will not execute if expected arguments are set and
     * do not match requirements.
     * If the command has a permission verifier, the given permissionObject will be
     * passed to it before the command is allowed to run.
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
            command.commandFunction?.invoke(args, source) ?: (return CommandResult.NO_COMMAND_FUNCTION)
        } else {
            return CommandResult.NO_MATCHING_COMMAND
        }
        return CommandResult.SUCCESS
    }
    /**
     * Process the given command, with arguments given as a single string.
     * The command will not execute if expected arguments are set and
     * do not match requirements.
     * Convenience method. Splits the arguments, treating items in quotation marks
     * as a single argument, passing them to process()
     */
    fun process(commandId: String, arg: String, permissionObject: P?=null, source: S?=null): CommandResult {
        return if (arg.trim()=="") {
            process(commandId,listOf(), permissionObject, source)
        } else {
            process(commandId, arg.split(' '), permissionObject, source)
        }
    }
    /**
     * Process the given command, with arguments given as a single string.
     * The command will not execute if expected arguments are set and
     * do not match requirements.
     * Convenience method. First argument is treated as commandId.
     * Splits the remaining arguments, treating items in quotation marks
     * as a single argument, passing them to process()
     */
    fun process(input: String, permissionObject: P?=null, source: S?=null): CommandResult {
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
    fun command(id: String, incFun: Command<S, P>.() -> Unit) {
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
    var commandFunction: ((List<String>, S?) -> Unit)? = null
    /**
     * Optional map for storing instanced data for the command without a need for subclassing.
     * Note that this data is unique to the Command object, not to each individual call.
     * Properties object is initialized lazily using kotlin's lazy delegate.
     */
    val properties: Map<String, String> by lazy { HashMap<String, String>() }
    /**
     * Alternative command names that will also activate this command.
     * Matching-wise they function the same as the base name of the command.
     * Initialized lazily using kotlin's lazy delegate.
     */
    val aliases: MutableList<String> by lazy {mutableListOf<String>()}

    fun verifyArgsCount(count: Int): Boolean {
        if (expectedArgsMin<0 && expectedArgsMax<0) return true
        if (expectedArgsMin>count) return false
        if (expectedArgsMax<count) return false
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
    fun func(incFunction: ((List<String>, S?) -> Unit)) {
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