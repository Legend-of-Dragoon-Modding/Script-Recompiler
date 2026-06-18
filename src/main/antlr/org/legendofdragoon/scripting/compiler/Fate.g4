grammar Fate ;

/*
 * Parser Rules
 */

body : (entrypoint | global | function)* ;

entrypoint : ENTRYPOINT IDENTIFIER TERM ;
function : DEF IDENTIFIER identifier_list block ;

if_ : IF LPAREN expression RPAREN block (ELSE if_ | ELSE block)? ;
while_ : WHILE LPAREN expression RPAREN block ;
do_while : DO block WHILE LPAREN expression RPAREN TERM ;
control : if_ | while_ | do_while ;

block : OPENER (statement | control)* CLOSER ;

statement : (declaration | assignment | postfix | augmented_assignment | call | return_ | CONTINUE | BREAK) TERM ;
augmented_assignment : assignable augmented_assignment_op expression ;
postfix : assignable postfix_op ;
assignment : (assignable | assignable_list) ASSIGN (expression | array_initializer) ;
declaration : VAR (IDENTIFIER | identifier_list) (ASSIGN (expression | array_initializer))? ;
global : VAR IDENTIFIER ASSIGN (NUMBER | const_array_initializer) TERM ;

call : IDENTIFIER (SCOPE IDENTIFIER)? expression_or_string_list ;
return_ : RETURN (expression | expression_list)? ;

identifier_list : LPAREN (IDENTIFIER (COMMA IDENTIFIER)*)? RPAREN ;
assignable_list : LPAREN (assignable (COMMA assignable)*)? RPAREN ;
expression_list : LPAREN (expression (COMMA expression)*)? RPAREN ;
expression_or_string_list : LPAREN (expression_or_string (COMMA expression_or_string)*)? RPAREN ;

expression_or_string : expression | STRING ;

expression :
  LPAREN expression RPAREN |
  (ADD | SUB | NOTC | NOTA) expression |
  expression mult_op expression |
  expression add_op expression |
  expression shift_op expression |
  expression relational_op expression |
  expression equality_op expression |
  expression ANDA expression |
  expression XORA expression |
  expression ORA expression |
  expression ANDC expression |
  expression ORC expression |
  value ;

value : NUMBER | call | assignable | id | array_lookup ;
assignable : IDENTIFIER | stor | gamevar | reg ;
stor : STOR LBRACKET (expression COMMA)? expression RBRACKET ;
gamevar : VAR LBRACKET expression RBRACKET (LBRACKET expression RBRACKET)? ;
reg : REG LBRACKET expression RBRACKET ;
id : IDENTIFIER COLON IDENTIFIER ;
array_initializer : LBRACKET expression (COMMA expression)* RBRACKET ;
array_lookup : IDENTIFIER LBRACKET expression RBRACKET ;
const_array_initializer : LBRACKET (NUMBER (COMMA NUMBER)* | STRING (COMMA STRING)*) COMMA? RBRACKET ;

augmented_assignment_op : ANDC_ASSIGN | ORC_ASSIGN | MUL_ASSIGN | DIV_ASSIGN | MOD_ASSIGN | ADD_ASSIGN | SUB_ASSIGN | ANDA_ASSIGN | ORA_ASSIGN | XORA_ASSIGN | NOTA_ASSIGN | SHR_ASSIGN | SHL_ASSIGN;
postfix_op : INCR | DECR ;
mult_op : MUL | DIV | MOD ;
add_op : ADD | SUB ;
shift_op : SHR | SHL ;
relational_op : GT | LT | GTE | LTE ;
equality_op : EQ | NEQ ;

/*
 * Lexer Rules
 */

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

STOR : 'stor' ;
REG : 'reg' ;
IDENTIFIER : [a-zA-Z_][a-zA-Z0-9_]* ;
NUMBER : '-'?([0-9]+|'0x'[a-f0-9]+) ;
WHITESPACE : [ \n] -> skip ;

LINE_COMMENT : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT : '/*' .*? '*/' -> skip ;

STRING : '"' ( ESC_SEQ | ~('\\' | '"') )* '"' ;

fragment ESC_SEQ : '\\' [n"\\] ;
