import io.github.talkarcabbage.commandlin.*
import org.junit.Test
import org.junit.Assert.*

class CommandlinTest {
    @Test
    fun commandlinTest() {
        val cmdMan = commandlin<List<String>, Int, SourceTest?> {
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
        val nothingTest = commandlin<Nothing?, Nothing?, Nothing?> {
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
        val authTestOptional = commandlin<List<String>, Int, SourceTest?> {
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
        val authTestMandatory = commandlin<List<String>, Int, SourceTest?> {
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
        val cmdMan = commandlin<List<String>, Int, SourceTest> {
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
        assertEquals(CommandResult.SUCCESS, cmdMan.process("noargs noargs", listOf("noargs"), 0, source=SourceTest("srconly")))
    }

    fun returnTypeTwo(): String { return ""  }
    fun returnTypeOne() {  }

    @Test
    fun testMisc() {
        val cmd = commandlin<List<String>, Nothing?, Author?> {
            command("test") {
                func { args, source ->
                    println("We have ${args.size} arguments from $source")
                }
            }
        }
        val someArguments = listOf("Arg one", "Arg two")
        val author = Author("Steve")
        println(cmd.process("test", someArguments, null, author))
    }
}

class SourceTest(var name: String)
class Author(var name: String)