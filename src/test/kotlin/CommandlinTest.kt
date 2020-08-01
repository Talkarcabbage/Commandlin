import io.github.talkarcabbage.commandlin.*
import org.junit.Test
import org.junit.Assert.*

class CommandlinTest {
    @Test
    fun commandlinTest() {
        val cmdMan = commandlin<SourceTest, Int> {
            command("basictest") {
                permissionVerifier = BasicPermissionVerifier(8)
                func { args, src->
                    println("Hallo, ${src?.name ?: "we had no source"}")
                }
            }
            command("argstest") {
                this.expectedArgsMax=1
                this.expectedArgsMin=1
                func { args, src ->
                    println("We got one arg successfully:${args[0]}")
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
        }
        val nothingTest = commandlin<Nothing, Nothing> {
            command("potato") {
                func { args, _ ->
                    println("I have Nothing!")
                }
            }
        }
        nothingTest.process("ayylmao","", null,null)
        assertEquals(cmdMan.process("basictest", listOf(), 8, SourceTest("Hi I'm a test")), CommandResult.SUCCESS)
        assertEquals(cmdMan.process("basictest", listOf(), 8, SourceTest("Hi I'm a test")), CommandResult.SUCCESS)
        assertEquals(cmdMan.process("basictest", listOf(), 8, null), CommandResult.SUCCESS)
        assertEquals(cmdMan.process("basictest", listOf(), 0, null), CommandResult.INSUFFICIENT_PRIVILEGES)
        assertEquals(cmdMan.process("argstest", listOf(), 0, null), CommandResult.INVALID_ARGUMENTS)
        assertEquals(cmdMan.process("argstest", listOf(""), 0, null), CommandResult.SUCCESS)
        assertEquals(cmdMan.process("argstest", listOf("",""), 0, null), CommandResult.INVALID_ARGUMENTS)
        assertEquals(cmdMan.process("asdf"), CommandResult.NO_MATCHING_COMMAND)
        assertEquals(CommandResult.NO_COMMAND_FUNCTION, cmdMan.process("warnmemissing"))
        nothingTest.process("potato", listOf())
        assertEquals(CommandResult.SUCCESS, cmdMan.process("aliased"))
    }

    @Test
    fun authTest() {
        val authTestOptional = commandlin<SourceTest, Int> {
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
        val authTestMandatory = commandlin<SourceTest, Int> {
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
        assertEquals(CommandResult.SUCCESS, authTestOptional.process("noauth"))
        assertEquals(CommandResult.INSUFFICIENT_PRIVILEGES, authTestOptional.process("underauth"))
        assertEquals(CommandResult.INSUFFICIENT_PRIVILEGES, authTestOptional.process("underauth", 1))
        assertEquals(CommandResult.SUCCESS, authTestOptional.process("allowedauth", 5))

        assertEquals(CommandResult.NO_MATCHING_COMMAND, authTestMandatory.process("noauth"))
        assertEquals(CommandResult.INSUFFICIENT_PRIVILEGES, authTestMandatory.process("underauth"))
        assertEquals(CommandResult.INSUFFICIENT_PRIVILEGES, authTestMandatory.process("underauth", 1))
        assertEquals(CommandResult.SUCCESS, authTestMandatory.process("allowedauth", 5))

    }
}

class SourceTest(var name: String)
