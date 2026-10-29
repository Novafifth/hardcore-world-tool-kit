package com.hardcoreworldtoolkit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import java.util.Optional;
import org.junit.Test;

public class DevCommandsTest
{
	private static Optional<DevCommands.Action> action(String... args)
	{
		return DevCommands.parse(args).map(DevCommands.Command::getAction);
	}

	@Test
	public void freshPreview()
	{
		assertEquals(Optional.of(DevCommands.Action.FRESH_ON), action("fresh"));
		assertEquals(Optional.of(DevCommands.Action.FRESH_ON), action("FRESH", "on"));
		assertEquals(Optional.of(DevCommands.Action.FRESH_OFF), action("fresh", "Off"));
	}

	@Test
	public void hints()
	{
		assertEquals(Optional.of(DevCommands.Action.HINTS), action("hints"));
	}

	@Test
	public void rejectsAnythingElse()
	{
		assertFalse(action().isPresent());
		assertFalse(DevCommands.parse(null).isPresent());
		assertFalse(action("fresh", "maybe").isPresent());
		assertFalse(action("hints", "now").isPresent());
		assertFalse(action("fresh", "off", "now").isPresent());
		assertFalse(action("unknown").isPresent());
		assertFalse(action("fresh", "on", "off").isPresent());
	}
}
