lexer grammar FateLexer ;

ENTRYPOINT : 'entrypoint' ;
DEF : 'def' ;
VAR : 'var' ;
TRUE : 'true' ;
FALSE : 'false' ;
RETURN : 'return' ;
CONTINUE : 'continue' ;
BREAK : 'break' ;

IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;
DO : 'do' ;

EQ : '==' ;
NEQ : '!=' ;
GT : '>' ;
LT : '<' ;
GTE : '>=' ;
LTE : '<=' ;
ANDC: '&&' ;
ORC: '||' ;
NOTC : '!' ;

MUL : '*' ;
DIV : '/' ;
MOD : '%' ;
ADD : '+' ;
SUB : '-' ;
ANDA : '&' ;
ORA : '|' ;
XORA : '^' ;
NOTA : '~' ;

SHR : '>>' ;
SHL : '<<' ;

ANDC_ASSIGN: '&&=' ;
ORC_ASSIGN: '||=' ;

MUL_ASSIGN : '*=' ;
DIV_ASSIGN : '/=' ;
MOD_ASSIGN : '%=' ;
ADD_ASSIGN : '+=' ;
SUB_ASSIGN : '-=' ;
ANDA_ASSIGN : '&=' ;
ORA_ASSIGN : '|=' ;
XORA_ASSIGN : '^=' ;
NOTA_ASSIGN : '~=' ;

SHR_ASSIGN : '>>=' ;
SHL_ASSIGN : '<<=' ;

ASSIGN : '=' ;
INCR : '++';
DECR : '--';

COMMA : ',' ;

LPAREN : '(' ;
RPAREN : ')' ;

LBRACKET : '[' ;
RBRACKET : ']' ;

OPENER : '{' ;
CLOSER : '}' ;
TERM : ';' ;

SCOPE : '::' ;
COLON : ':' ;

INCLUDE : 'include' ' '+ -> pushMode(INCLUDE_FILE_MODE) ;

STOR : 'stor' ;
REG : 'reg' ;
IDENTIFIER : [a-zA-Z_][a-zA-Z0-9_]* ;
NUMBER : '-'?([0-9]+|'0x'[a-f0-9]+) ;
WHITESPACE : [ \n] -> skip ;

LINE_COMMENT : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT : '/*' .*? '*/' -> skip ;

STRING : '"' ( ESC_SEQ | ~('\\' | '"') )* '"' ;

fragment ESC_SEQ : '\\' [n"\\] ;

mode INCLUDE_FILE_MODE;
INCLUDE_FILE : ~[;\r\n]+ -> popMode ;
