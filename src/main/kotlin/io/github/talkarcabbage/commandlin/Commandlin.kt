package io.github.talkarcabbage.commandlin

/**
 * Represents the result of a command.
 *
 * These values are returned from the [CommandlinManager.process] function to determine
 * whether a command executed successfully or not.
 */
enum class CommandResult {
    SUCCESS,
    INSUFFICIENT_PRIVILEGES,
    NO_MATCHING_COMMAND,
    INVALID_ARGUMENTS,
    NO_COMMAND_FUNCTION,
    GENERIC_FAILURE
}



/**
 *
 * Creates an instance of the command manager, with a DSL type structure for convenience.
 *
 * A: The type that holds the arguments passed to the command. This is often a list of strings in many cases,
 * but is left generic for various use cases.
 *
 * P: The type used by the PermissionVerifier implementation used. The built-in implementation,
 * BasicPermissionVerifier, uses Int as its type.
 *
 * S: The type representing the source of the command. Can be any type. Passed to command when executed.
 *
 * If you do not need a source and/or permission type, passing the [Nothing]? type should work.
 *
 */
fun <A, P, S> commandlin(incFun: CommandlinManager<A, P, S>.() -> Unit): CommandlinManager<A, P, S> {
    val commandlinBuilder = CommandlinManager<A, P, S>()
    commandlinBuilder.incFun()
    return commandlinBuilder
}

class CommandlinManager<A, P, S> {
    val commands: MutableMap<String, Command<A, P, S>> = mutableMapOf()
    /**
     * If true, every command must have a permissions object assigned during creation, or it will not be added.
     * It cannot be disabled after being enabled!
     */
    var requireCommandPermission=false
        set(enabled) {
            if (enabled) field = true
        }

    /**
     * If set, this registrar is called when a command is added to
     * the command manager.
     */
    var commandRegistrar: CommandRegistrar<A, P, S>? = null

    private fun addCommand(id: String, com: Command<A, P, S>) = commands.put(id, com)
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
    fun process(commandId: String, args: A, permissionObject: P, source: S): CommandResult {
        val command: Command<A, P, S>? = findMatchingCommand(commandId)
        if (command != null) {
            val cpv=command.permissionVerifier //Temporary variables to help with some thread safety nullable stuff
            val argV = command.argumentsVerifier
            if (!(command.permissionVerifier==null || (cpv!=null && permissionObject!=null && cpv.checkPermission(command, permissionObject)))) {
                return CommandResult.INSUFFICIENT_PRIVILEGES
            }
            if (argV!=null && !(argV.verifyArguments(args))) {
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

    fun getAutocomplete(commandID: String, args: A): List<A>? {
        val cmd = findMatchingCommand(commandID)
        return cmd?.autocompleteHandler?.getSuggestions(args)
    }

    /**
     * This function returns a matching command
     */
    private fun findMatchingCommand(commandID: String, checkAliases: Boolean = true): Command<A, P, S>? {
        val foundCmd =  commands.getOrDefault(commandID, null)
        return if (foundCmd == null && checkAliases) {
            commands.values.firstOrNull {it.aliases.contains(commandID)}
        } else {
            return foundCmd
        }
    }

    /**
     * Add a command to the command manager.
     * The commandlin instance should ideally be configured prior to calling this function.
     * The command may be configured from within this function.
     * This function will throw an IllegalStateException if requireCommandPermission is set and
     * the command does not have a permission verifier set in its lambda.
     */
    fun command(id: String, incFun: Command<A, P, S>.() -> Any?) {
        if (findMatchingCommand(id)!=null) {
            println("Failed to add a command that already exists or has an alias: $id")
            return
        }
        val newCmd = Command<A, P, S>(id)
        newCmd.incFun()
        if (requireCommandPermission && newCmd.permissionVerifier==null) {
            throw IllegalStateException("Attempted to add a command with no permission verifier: ${newCmd.id}")
        }
        if (newCmd.commandFunction==null) {
            println("The following command was added with no command function, so it will no-op: ${newCmd.id}")
        }
        addCommand(id, newCmd)
        commandRegistrar?.handleRegistration(newCmd)
    }

    /**
     * Can be called if there is a need to remove a command from the manager.
     * This function will also call the [CommandRegistrar.handleDeregistration] function
     * if there is an attached registrar, if a matching command was removed.
     * This function will do nothing if there was no matching command.
     * This function does NOT check aliases.
     */
    fun removeCommand(id: String) {
        val removed = commands.remove(id)
        if (removed != null) commandRegistrar?.handleDeregistration(removed)
    }
}

/**
 * An implementation to hold the logic to manage individual commands and their functions.
 * S refers to the data type of the source object.
 * P refers to the data type of object used in the permission verifier, if it is used.
 * A refers to the data type of the command's arguments. Can be as simple as a string, or any
 * object by convenience.
 *
 */
class Command<A, P, S>(var id: String) {
    var argumentsVerifier: ArgumentsVerifier<A>? = null
    /**
     * The permissions verifier object for this command. If it is non-null, this
     * verifier will be called with the given P object and must return true for the command to execute.
     */
    var permissionVerifier: PermissionVerifier<P>? = null

    var autocompleteHandler: AutocompleteHandler<A>? = null
    /**
     * The actual function/lambda to execute.
     * Receives the Arguments and the Source of the call as parameters.
     */
    var commandFunction: ((A, S) -> Any?)? = null
    /**
     * Optional map for storing instanced data for the command without a need for subclassing.
     * Note that this data is unique to the Command object, not to each individual call.
     * Properties object is initialized lazily using kotlin's lazy delegate.
     */
    val properties: MutableMap<String, Any> by lazy { HashMap() }
    /**
     * Alternative command names that will also activate this command.
     * Matching-wise they function the same as the base name of the command.
     * Initialized lazily using kotlin's lazy delegate.
     */
    val aliases: MutableList<String> by lazy {mutableListOf()}

    fun alias(name: String) {
        aliases.add(name)
    }

    /**
     * The function of the command. This function is called when
     * the command with a matching ID or alias is sent to [CommandlinManager.process],
     * after the permissions verifier, if present, has ensured the command can be executed,
     * and the arguments verifier, if present, has validated the command's arguments.
     */
    fun func(incFunction: ((A, S) -> Any?)) {
        this.commandFunction = incFunction
    }

    /**
     * Takes in a function for commands that do not have or do not need to process arguments.
     * Allows for excluding parameters declaration, allowing for using just a parameter
     * which represents the Source object.
     * Function name is separate from funcNS to prevent a duplicate signature situation.
     * @see [Command.func]
     */
    fun funcNA(incFunctionNoArgs: ((S?) -> Any?)) {
        this.func {_, src ->
            incFunctionNoArgs(src)
        }
    }

    /**
     * Takes in a function for commands that do not need to process a Source object.
     * Allows for excluding explicit parameter declarations, allowing for using just a parameter
     * which represents the passed arguments.
     *
     * Function name is separate from funcNA to prevent a duplicate signature situation (S=List<String>)
     * @see [Command.func]
     */
    fun funcNS(incFunctionNoS: ((A)->Any?)) {
        this.func {args, _ ->
            incFunctionNoS(args)
        }
    }

    /**
     * Takes in a function for commands that does not process parameters.
     * Useful for avoiding redundant declarations of variables
     * The presence of arguments or a source object are ignored entirely.
     * Function name is separate from func() to avoid confusing the two should
     * arguments accidentally be excluded when declaring a command's function.
     *
     * @see [Command.func]
     */
    fun funcNP(incFunction: ()->Any?) {
        this.commandFunction = {_,_->
            incFunction()
        }
    }

}

/**
 * An interface for defining a PermissionVerifier, which can be used
 * to check whether a command is allowed to execute given a specified
 * object to verify. The provided implementation is [BasicPermissionVerifier]
 */
interface PermissionVerifier<T> {
    fun checkPermission(command: Command<*, T, *>, authToken: T): Boolean
}

/**
 * A simple permission verifier for commands. It returns true if the value
 * provided is higher than or equal to the number it is instantiated with.
 */
class BasicPermissionVerifier(val requiredLevel: Int): PermissionVerifier<Int>  {
    override fun checkPermission(command: Command<*, Int, *>, authToken: Int): Boolean {
        return (authToken>=this.requiredLevel)
    }
}

/**
 * An interface for optionally implementing an arguments verifier for a command.
 *
 */
fun interface ArgumentsVerifier<A> {
    /**
     * This function is called by the command manager prior to the command
     * being executed. If it returns false, the command will not execute and
     * will return an INVALID_ARGUMENTS result.
     * For a simple implementation that receives the arguments and counts them against
     * a min and max expected count, see [ArgumentCountVerifier]
     *
     */
    fun verifyArguments(args: A): Boolean
}

fun interface AutocompleteHandler<A> {
    /**
     * This function can be called by requesting autocomplete predictions
     * from the command manager when a command and arguments are passed in.
     *
     */
    fun getSuggestions(args: A): List<A>
}

/**
 * An instance of this interface can be added to a command manager, and it
 * is called when a command is added to the manager. This allows for easy
 * supplemental registration of commands, such as adding them to an api or
 * setting up callbacks.
 * This interface is optional and is not required for operation.
 */
interface CommandRegistrar<A, P, S> {
    /**
     * Called when a command is added to the manager so that it can be
     * managed in any additional ways needed.
     */
    fun handleRegistration(command: Command<A, P, S>)
    fun handleDeregistration(command: Command<A, P, S>)
}

/**
 * This implementation of ArgumentsVerifier ensures the given argument count falls between
 * the min and max specified.
 */
class ArgumentCountVerifier(val minArgs: Int, val maxArgs: Int): ArgumentsVerifier<List<String>> {
    override fun verifyArguments(args: List<String>): Boolean {
        val argsCount = args.size
        return (argsCount in minArgs.. maxArgs)
    }
}

/**
 * A convenience function that splits a string into its first delimited (space by default) substring
 * and a list of subsequent strings delimited by that character.
 * The resulting return format is a Pair (String, List<String>).
 * For example, the string "foo bar fizz" would return Pair("Foo", List("bar", "fizz"))
 */
fun evaluateArguments(argString: String, delimiter: String = " "): Pair<String, List<String>> {
    return Pair(argString.substringBefore(delimiter), argString.substringAfter(delimiter, missingDelimiterValue = "").split(delimiter))
}