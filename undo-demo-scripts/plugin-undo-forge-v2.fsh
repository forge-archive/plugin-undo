@currentStep = 1;
@v = SHELL.prompt("Start at which step?");
@startAt = Integer.parseInt( "".equals(v.trim()) ? 1 : v );

def step( cmd ) { 

	if ( startAt <= currentStep )
	{
		@SHELL.println();
		if ( SHELL.promptBoolean("Execute " + currentStep + ": " + cmd + " ?") )
		{
			@SHELL.println();
			$cmd;
			@SHELL.println();
			wait;
			clear;
		}
	}
	currentStep ++;

};

clear;
@step("new-project --named conftrack --topLevelPackage com.conftrack");
@step("undo setup");
@step("java new-class --package com.conftrack "class One{}");
@step("java new-class --package com.conftrack "class Two{}");
@step("undo restore");
@step("undo restore");
