package com.enoughisasgoodasafeast.cli;


import com.enoughisasgoodasafeast.Message;
import com.enoughisasgoodasafeast.PlatformGateway;
import com.enoughisasgoodasafeast.RecordingHandlerListener;
import org.fusesource.jansi.AnsiConsole;
import org.jline.builtins.ConfigurationPath;
import org.jline.console.SystemRegistry;
import org.jline.console.impl.Builtins;
import org.jline.console.impl.SystemRegistryImpl;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.shell.jline3.PicocliCommands;
import picocli.shell.jline3.PicocliCommands.PicocliCommandsFactory;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.enoughisasgoodasafeast.SharedConstants.BRBL_ENQUEUE_ENDPOINT;

/**
 * CLI client for sending messages and getting responses.
 * 1) create persona
 * > input telephone number
 * > input shortcode
 * < display newly created persona
 * 2) list personas
 * < display all available personas with id number (write these to a serialized file that can be loaded at start up)
 * 3) send message (initially just a send but being able to queue messages and send as a batch will be useful.)
 * > display "Which persona will send the message?" then list available personas by id
 * > input selected id
 * < display selected persona then prompt "Enter the text of the message:"
 * > input message text
 * < display confirmation dialog "Send message? y/n"
 * > input choice (if yes, send message)
 * > wait for response (5 seconds) and display it. Otherwise, display "No response received."
 * 4) save personas
 * 5) exit
 */
public class BrblCli {

    static AtomicInteger nextPersonaId = new AtomicInteger(0);
    static AtomicInteger nextMessageId = new AtomicInteger(0);

    static List<Persona> userPersonas = new ArrayList<>();
    static List<String> sentMessages = new ArrayList<>();
    static List<String> receivedMessages = new ArrayList<>();

    static Persona currentUserPersona;
    static String currentMessage;

    static PlatformGateway platformGateway;

    // FIXME this is temporary, until we have persona load/save working
    static {
        userPersonas.add(new Persona(1, "17811234343", "21249"));
        userPersonas.add(new Persona(2, "17811238659", "98712"));
        userPersonas.add(new Persona(3, "17811235964", "45678"));
        nextPersonaId.set(userPersonas.size());
    }

    @Command(name = "brblcli",
            description = {
                    "Hit @|magenta <TAB>|@ to see available commands."},
            footer = {"", "Press Ctrl-D to exit."},
            subcommands = {
                    DisplayPersonas.class, CreatePersona.class, LoadPersonas.class,
                    SendMessage.class,
                    PicocliCommands.ClearScreen.class, CommandLine.HelpCommand.class
            }
    )
    static class MainCommands implements Runnable {
        PrintWriter out;
//        NonBlockingReader in;
        LineReader lineReader;

        public void setReader(LineReader reader) {
            out = reader.getTerminal().writer();
            lineReader = reader;
        }

        public LineReader getReader() {
            return lineReader;
        }

        @Override
        public void run() {
            out.println(new CommandLine(this).getUsageMessage());
        }
    }

    @Command(name = "DisplayPersonas", mixinStandardHelpOptions = true, version = "0.1",
            description = {"Display the set of available personas"},
            subcommands = {CommandLine.HelpCommand.class})
    static class DisplayPersonas implements Runnable {

        @CommandLine.ParentCommand
        BrblCli.MainCommands parent;

        public void run() {
            parent.out.println("User personas:");
            userPersonas.forEach(s -> parent.out.println(s));
        }
    }

    @Command(name = "CreatePersona", mixinStandardHelpOptions = true, version = "0.1",
            description = {"Create a new persona to the set of available personas"},
            subcommands = {CommandLine.HelpCommand.class})
    static class CreatePersona implements Runnable {

        @CommandLine.ParentCommand
        BrblCli.MainCommands parent;

        public void run() {
            parent.out.println("Enter: ");
            parent.out.print("  phoneNumber: ");
            String phoneNum = parent.getReader().readLine();
            parent.out.print("  shortCode: ");
            String shortCode = parent.getReader().readLine();
            Persona newPersona = new Persona(nextPersonaId(), phoneNum, shortCode);
            parent.out.println(newPersona);
            userPersonas.add(newPersona);
            currentUserPersona = newPersona;
        }
    }
    @Command(name = "LoadPersonas", mixinStandardHelpOptions = true, version = "0.1",
            description = {"Load personas from a file."},
            subcommands = {CommandLine.HelpCommand.class})
    static class LoadPersonas implements Runnable {

        @CommandLine.ParentCommand
        BrblCli.MainCommands parent;

        public void run() {
            parent.out.println("LoadPersonas");
        }
    }

    @Command(name = "SendMessage", mixinStandardHelpOptions = true, version = "0.1",
            description = {"Send message to a known user persona"},
            subcommands = {CommandLine.HelpCommand.class})
    static class SendMessage implements Runnable {

        @CommandLine.ParentCommand
        BrblCli.MainCommands parent;

        public void run() {
            parent.out.println("SendMessage");
            userPersonas.forEach(s -> parent.out.println(s));
            parent.out.print("Select Persona by id: ");
            int personaId = Integer.parseInt(parent.getReader().readLine());

            for (Persona userPersona : userPersonas) {
                if (userPersona.id == personaId) {
                    currentUserPersona = userPersona;
                }
            }
            if (null == currentUserPersona) {
                parent.out.println("You must select a Persona to send messages.");
                return;
            }
            parent.out.println("Current " + currentUserPersona);
            parent.out.print("Enter message text:");
            String msg = parent.getReader().readLine();

            Message structuredMsg = Message.newMO(
                    currentUserPersona.phoneNumber,
                    currentUserPersona.shortCode,
                    msg);

            platformGateway.sendMoTraffic(structuredMsg);

            //... actually send the message as long it's not empty.
            parent.out.println("Message sent: " + msg);
            sentMessages.add(msg);
            currentMessage = msg;
        }
    }

    public static void main(String... args) {
        AnsiConsole.systemInstall(); // needed for Linux & Mac? Or just Windows?

        try {
            // Not sure that we need the next two lines
            Supplier<Path> workDir = () -> Paths.get(System.getProperty("user.dir"));
            Builtins builtins = new Builtins(workDir, new ConfigurationPath(workDir.get(), workDir.get()), null);

            MainCommands mainCommands = new MainCommands();

            PicocliCommandsFactory factory = new PicocliCommandsFactory();

            CommandLine cmd = new CommandLine(mainCommands, factory);
            PicocliCommands picocliCommands = new PicocliCommands(cmd);

            Parser parser = new DefaultParser();
            try (Terminal terminal = TerminalBuilder.builder().build()) {
                SystemRegistry systemRegistry = new SystemRegistryImpl(parser, terminal, workDir, null);
                systemRegistry.setCommandRegistries(builtins, picocliCommands);
                systemRegistry.register("help", picocliCommands);

                LineReader reader = LineReaderBuilder.builder()
                        .terminal(terminal)
                        .completer(systemRegistry.completer())
                        .parser(parser)
                        .variable(LineReader.LIST_MAX, 50)   // max tab completion candidates
                        .build();
                builtins.setLineReader(reader);
                mainCommands.setReader(reader);
                factory.setTerminal(terminal);
                // start the shell and process input until the user quits with Ctrl-D
                String prompt = "brblcli> ";
                String rightPrompt = ""; // What's a rightPrompt?
                String line;

                platformGateway = new PlatformGateway("http://192.168.1.155:4242" + BRBL_ENQUEUE_ENDPOINT);
                platformGateway.init();

                // Add callback to print the MT arriving
                platformGateway.recordingHandler.addListener(new RecordingHandlerListener() {
                    @Override
                    public void notify(String message) {
                        reader.getTerminal().writer().println("MT received: " + message);
                        reader.getTerminal().writer().flush();
                        reader.getTerminal().writer().println("");
                    }
                });

                while (true) {
                    try {
                        systemRegistry.cleanUp();
                        line = reader.readLine(prompt, rightPrompt, (MaskingCallback) null, null);
                        systemRegistry.execute(line);
                    } catch (UserInterruptException e) {
                        System.out.println("Hmm, UserInterruptException caught: " + e);
                    } catch (EndOfFileException e) {
                        System.out.println("Exiting.");
                        platformGateway.stop();
                        return;
                    } catch (Exception e) {
                        systemRegistry.trace(e);
                    }
                }
            }

        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            AnsiConsole.systemUninstall();
        }
    }

    static int nextPersonaId() {
        return nextPersonaId.incrementAndGet();
    }

    static int nextMessageId() {
        return nextMessageId.incrementAndGet();
    }

    public record Persona(Integer id, String phoneNumber, String shortCode){};

}
