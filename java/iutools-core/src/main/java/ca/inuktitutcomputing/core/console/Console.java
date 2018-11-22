package ca.inuktitutcomputing.core.console;

import org.apache.commons.cli.Option;
import org.apache.log4j.helpers.OptionConverter;

import ca.nrc.ui.commandline.CommandLineException;
import ca.nrc.ui.commandline.MainCommand;
import ca.nrc.ui.commandline.SubCommand;;

public class Console {
	
	protected static MainCommand defineMainCommand() throws CommandLineException {
		MainCommand mainCmd = new MainCommand("Command line console for iutools.");

		Option optCorpusDir = Option.builder(null)
				.longOpt(ConsoleCommand.OPT_CORPUS_DIR)
			    .desc("Path of a directory contains files for a corpus to be processed.")
			    .hasArg()
			    .argName("CORPUS_DIR")
			    .build();

		Option optTrieFile = Option.builder(null)
				.longOpt(ConsoleCommand.OPT_TRIE_FILE)
			    .desc("Path of json file where Trie is saved.")
			    .hasArg()
			    .argName("TRIE_FILE")
			    .build();

		Option optCorpusName = Option.builder(null)
				.longOpt(ConsoleCommand.OPT_CORPUS_NAME)
			    .desc("Name of the corpus to be processed.")
			    .hasArg()
			    .argName("CORPUS_NAME")
			    .build();

		// Compile a trie and save it to file
		SubCommand compileTrie = 
				new CmdCompileTrie("compile_trie")
				.addOption(optCorpusDir)				
				.addOption(optTrieFile)
				;
		mainCmd.addSubCommand(compileTrie);
		
		
		// Create and add the read_trie command
		SubCommand searchTrie = 
				new CmdSearchTrie("search_trie")
				.addOption(optTrieFile)				
				;
		mainCmd.addSubCommand(searchTrie);
		

				
		return mainCmd;
	}

	public static void main(String[] args) throws Exception {
		MainCommand mainCmd = defineMainCommand();
		mainCmd.run(args);
	}
	
//	public ConsoleCommand getConsoleCommand(String commandName) throws CommandLineException {
//		MainCommand mainCmd = defineMainCommand();
//		ConsoleCommand cmd = (ConsoleCommand) mainCmd.getSubCommandWithName(commandName);
//		
//		return cmd;
//	}
//
//	public static void runCommand(String cmdName, String[] args) throws Exception {
//		String[] commandWithArgs = new String[args.length+3];
//		commandWithArgs[0] = cmdName;
//		for (int ii=0; ii < args.length; ii++) {
//			commandWithArgs[ii+1] = args[ii];
//		}
//		commandWithArgs[commandWithArgs.length-2] = "-verbosity";
//		commandWithArgs[commandWithArgs.length-1] = "null";
//		
//		Console.main(commandWithArgs);
//		
//		Thread.sleep(1*1000);
//	}	
}
