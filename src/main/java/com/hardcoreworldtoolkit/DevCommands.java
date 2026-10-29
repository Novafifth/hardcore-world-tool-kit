package com.hardcoreworldtoolkit;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.Value;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.eventbus.Subscribe;

/**
 * The {@code ::hwtk} developer command: a fresh-install preview of the panel and re-arming the one-time hints.
 * Registered on the event bus only in developer mode; normal installs never see it. Runs on the client thread.
 */
final class DevCommands
{
	static final String COMMAND = "hwtk";
	static final String USAGE = "Usage: ::hwtk fresh | fresh off | hints";

	enum Action
	{
		/** Show the panel as a brand-new install would (UI only; nothing real changes). */
		FRESH_ON,
		/** Back to the real state. */
		FRESH_OFF,
		/** Re-arm the one-time chat hint and show it now. */
		HINTS,
	}

	@Value
	static class Command
	{
		Action action;
	}

	/** What the command drives. */
	interface Target
	{
		void freshPreview(boolean on);

		void resetHints();
	}

	private final Target target;
	/** Shows a line in the local chatbox only. */
	private final Consumer<String> chat;

	DevCommands(Target target, Consumer<String> chat)
	{
		this.target = target;
		this.chat = chat;
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted e)
	{
		if (COMMAND.equalsIgnoreCase(e.getCommand()))
		{
			run(e.getArguments());
		}
	}

	/** Run {@code ::hwtk <args>}. Client thread. */
	void run(String[] args)
	{
		Optional<Command> parsed = parse(args);
		if (!parsed.isPresent())
		{
			chat.accept(USAGE);
			return;
		}
		switch (parsed.get().getAction())
		{
			case FRESH_ON:
				target.freshPreview(true);
				chat.accept("Fresh-install preview on (panel only; nothing real changed). ::hwtk fresh off to end it.");
				break;
			case FRESH_OFF:
				target.freshPreview(false);
				chat.accept("Fresh-install preview off.");
				break;
			case HINTS:
				target.resetHints();
				break;
		}
	}

	/** {@code fresh [on|off]} or {@code hints}; case-insensitive. */
	static Optional<Command> parse(String[] args)
	{
		if (args == null || args.length == 0)
		{
			return Optional.empty();
		}
		String first = args[0].toLowerCase(Locale.ROOT);
		String second = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : null;
		switch (first)
		{
			case "fresh":
				if (args.length > 2)
				{
					return Optional.empty();
				}
				if (second == null || second.equals("on"))
				{
					return Optional.of(new Command(Action.FRESH_ON));
				}
				return second.equals("off") ? Optional.of(new Command(Action.FRESH_OFF)) : Optional.empty();
			case "hints":
				return args.length == 1 ? Optional.of(new Command(Action.HINTS)) : Optional.empty();
			default:
				return Optional.empty();
		}
	}
}
