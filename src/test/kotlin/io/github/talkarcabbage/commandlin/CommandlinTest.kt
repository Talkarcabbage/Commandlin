package io.github.talkarcabbage.commandlin

import org.junit.Test
import org.junit.Assert.*

class CommandlinTest {
    @Test
    fun commandlinTest() {
        val cmdMan = commandlin<List<String>, Int, SourceTest?, Map<String, Any>> {
            command("basictest") {
                permissionVerifier = BasicPermissionVerifier(8)
                func { _, src->
                    println("Hallo, ${src?.name ?: "we had no source"}")
                }
            }
            command("argstest") {
                this.argumentsVerifier=ArgumentCountVerifier(1,1)
                func { args, _ ->
                    println("We got ${args.size} arg successfully:${args[0]}")
                }
            }
            command("argstestmin") {
                this.argumentsVerifier=ArgumentCountVerifier(1,1000)
                func { args, _ ->
                    println("We got ${args.size} arg successfully:${args[0]}")
                }
            }
            command("warnmemissing") {

            }
            command("aliastest") {
                alias("aliased")
                func {_,_->
                    println("I should print and be successful!")
                }
            }
            command("returntest") {
                func {_,_ ->
                    return@func CommandResult.INVALID_ARGUMENTS
                }
            }
            command("returntypetest") {
                func {args,_ ->
                    if (args.isNotEmpty() && args[0]=="true") {
                        returnTypeOne() //Has no return statement at all
                    } else {
                        returnTypeTwo() //Returns an empty String
                    }
                }
            }
            command("thiscommandfails") {
                funcNP {
                    return@funcNP CommandResult.GENERIC_FAILURE
                }
            }
        }
        val nothingTest = commandlin<Nothing?, Nothing?, Nothing?, Nothing?> {
            command("potato") {
                func { _, _ ->
                    println("I have Nothing!")
                }
            }
        }
        assertEquals( CommandResult.SUCCESS, cmdMan.process("argstestmin", listOf("asdf"), 0, null))
        nothingTest.process("ayylmao",null, null,null)
        assertEquals(cmdMan.process("basictest", listOf(), 8, SourceTest("Hi I'm a test")), CommandResult.SUCCESS)
        assertEquals(cmdMan.process("basictest", listOf(), 8, SourceTest("Hi I'm a test")), CommandResult.SUCCESS)
        assertEquals(cmdMan.process("basictest", listOf(), 8, null), CommandResult.SUCCESS)
        assertEquals(cmdMan.process("basictest", listOf(), 0, null), CommandResult.INSUFFICIENT_PRIVILEGES)
        assertEquals(cmdMan.process("argstest", listOf(), 0, null), CommandResult.INVALID_ARGUMENTS)
        assertEquals(cmdMan.process("argstest", listOf(""), 0, null), CommandResult.SUCCESS)
        assertEquals(cmdMan.process("argstest", listOf("",""), 0, null), CommandResult.INVALID_ARGUMENTS)
        nothingTest.process("potato", null, null, null)
        assertEquals(cmdMan.process("thiscommandfails", listOf(), 0, null), CommandResult.GENERIC_FAILURE)
    }

    @Test
    fun authTest() {
        val authTestOptional = commandlin<List<String>, Int, SourceTest?, Map<String, Any>> {
            command("noauth") {
                func { _, _ ->
                    println("I should execute! I have no auth object!")
                }
            }
            command("underauth") {
                permissionVerifier = BasicPermissionVerifier(900001)
                func {_,_->
                    println("I should not execute!")
                    error("Executed without permission!")
                }
            }
            command("allowedauth") {
                permissionVerifier = BasicPermissionVerifier(5)
                func {_,_->
                    println("I should execute!")
                }
            }
        }
        val authTestMandatory = commandlin<List<String>, Int, SourceTest?, Map<String, Any>> {
            requireCommandPermission=true
            var noAuthProvidedPassed=false
            try {
                command("noauth") {
                    func { _, _ ->
                        println("I should not execute! I have no auth object! I shouldn't be added!")
                        error("Non-authed object executed in requireCommandPermission zone")
                    }
                }
            } catch (e: IllegalStateException) {
                noAuthProvidedPassed = true
            } finally {
                if (!noAuthProvidedPassed) error("Registered a command when it should have thrown an error!")
            }
            command("underauth") {
                permissionVerifier = BasicPermissionVerifier(900001)
                func {_,_->
                    println("I should not execute either!")
                    error("Executed without permission!")
                }
            }
            command("allowedauth") {
                permissionVerifier = BasicPermissionVerifier(5)
                func {_,_->
                    println("I should execute!")
                }
            }
        }
        assertEquals(CommandResult.SUCCESS, authTestOptional.process("noauth", listOf(), 0, null))
        assertEquals(CommandResult.INSUFFICIENT_PRIVILEGES, authTestOptional.process("underauth", listOf(), 0, null))
        assertEquals(CommandResult.INSUFFICIENT_PRIVILEGES, authTestOptional.process("underauth", listOf(),  1, null))
        assertEquals(CommandResult.SUCCESS, authTestOptional.process("allowedauth", listOf(), 5, null))

        assertEquals(CommandResult.NO_MATCHING_COMMAND, authTestMandatory.process("noauth", listOf(), 0, null))
        assertEquals(CommandResult.INSUFFICIENT_PRIVILEGES, authTestMandatory.process("underauth", listOf(), 0, null))
        assertEquals(CommandResult.INSUFFICIENT_PRIVILEGES, authTestMandatory.process("underauth", listOf(), 1, null))
        assertEquals(CommandResult.SUCCESS, authTestMandatory.process("allowedauth", listOf(), 5, null))
    }

    @Test
    fun testOptionals() {
        val cmdMan = commandlin<List<String>, Int, SourceTest, Map<String, Any>> {
            command("noargs") {
                funcNA {
                    println("Noargs from $it")
                }
            }
            command("nosrc") {
                funcNS {
                    println("We got only args ${it[0]}")
                }
            }
            command("noparams") {
                funcNP {
                    println("We got no parameters at all!")
                }
            }
        }
        val cmdPair = evaluateArguments("noargs noargs")
        assertEquals(CommandResult.SUCCESS, cmdMan.process(cmdPair.first, cmdPair.second, 0, source= SourceTest("srconly")))
    }

    fun returnTypeTwo(): String { return ""  }
    fun returnTypeOne() {  }

    @Test
    fun testMisc() {
        val cmd = commandlin<List<String>, Nothing?, Author?, Nothing?> {
            command("test") {
                autocompleteHandler = AutocompleteHandler {
                    _ ->
                    println()
                    listOf(listOf("testArg"))
                }
                func { args, source ->
                    println("We have ${args.size} arguments from $source")
                }
            }
        }
        val someArguments = listOf("Arg one", "Arg two")
        val author = Author("Steve")
        println(cmd.getAutocomplete("test", listOf()))
        println((cmd.getAutocomplete("test", listOf())))
        val (first) = (cmd.getAutocomplete("test", listOf()))!!
        assertTrue(first[0]=="testArg")
        println(cmd.process("test", someArguments, null, author))
    }

    @Test
    fun testCustomProps() {
        val cmd = commandlin<List<String>, Nothing?, Author?, PropertiesTest> {
            command("test") {
                properties = PropertiesTest("test", 5)
                func { _, _ ->
                    println("Our properties is ${properties?.commandPropertyString} and value of ${properties?.commandPropertyInt}")
                }
            }
        }
        cmd.process("test", listOf(), null, null)
        val test = cmd.commands["test"]
        assertEquals(5, test?.properties?.commandPropertyInt)
    }

    @Test
    fun testSimple() {
        val cmd = simpleCommandlin {
            command("hello") {
                funcNP {
                    println(" world!")
                }
            }
        }
        cmd.process("hello", null,null,null)
    }

    @Test
    fun testReadme() {
        var readmeCMD: CommandlinManager<List<String>, Int, Author?, Props>? = null
        readmeCMD = commandlin {
            requireCommandPermission = true
            command("test") {
                properties = Props("This is a test command!")
                permissionVerifier = BasicPermissionVerifier(1) //Built-in integer permission system - permission value in .process() must be equal or greater than this value to execute
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

}

class PropertiesTest(var commandPropertyString: String, var commandPropertyInt: Int)

class SourceTest(var name: String)
class Author(var name: String)
class Props(var commandHelpText: String)
