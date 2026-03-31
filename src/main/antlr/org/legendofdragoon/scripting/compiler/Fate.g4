grammar Fate ;

/*
 * Parser Rules
 */

body : (entrypoint | global | function)* ;

entrypoint : ENTRYPOINT IDENTIFIER TERM ;
function : DEF IDENTIFIER identifier_list block ;

if_ : IF LPAREN expression RPAREN block (ELSE if_ | ELSE block)? ;
while_ : WHILE LPAREN expression RPAREN block ;
control : if_ | while_ ;

block : OPENER (statement | control)* CLOSER ;

statement : (declaration | assignment | postfix | call | return_) TERM ;
postfix : IDENTIFIER postfix_op ;
assignment : (assignable | assignable_list) ASSIGN (expression | array_initializer) ;
declaration : VAR (IDENTIFIER | identifier_list) (ASSIGN (expression | array_initializer))? ;
global : VAR IDENTIFIER ASSIGN (NUMBER | const_array_initializer) TERM ;

call : IDENTIFIER (SCOPE IDENTIFIER)? expression_list ;
return_ : RETURN (expression | expression_list)? ;

identifier_list : LPAREN (IDENTIFIER (COMMA IDENTIFIER)*)? RPAREN ;
assignable_list : LPAREN (assignable (COMMA assignable)*)? RPAREN ;
expression_list : LPAREN (expression (COMMA expression)*)? RPAREN ;

expression :
  LPAREN expression RPAREN |
  (ADD | SUB | NOTC | NOTA) expression |
  expression comp_op expression |
  expression mult_op expression |
  expression add_op expression |
  expression shift_op expression |
  expression bit_op expression |
  value ;

value : NUMBER | call | assignable | id | array_lookup ;
assignable : IDENTIFIER | stor | gamevar | reg ;
stor : STOR LBRACKET (expression COMMA)? expression RBRACKET ;
gamevar : VAR LBRACKET expression RBRACKET (LBRACKET expression RBRACKET)? ;
reg : REG LBRACKET expression RBRACKET ;
id : IDENTIFIER COLON IDENTIFIER ;
array_initializer : LBRACKET expression (COMMA expression)* RBRACKET ;
array_lookup : IDENTIFIER LBRACKET expression RBRACKET ;
const_array_initializer : LBRACKET NUMBER (COMMA NUMBER)* RBRACKET ;

postfix_op : INCR | DECR ;
comp_op : EQ | NEQ | GT | LT | GTE | LTE | ANDC | ORC ;
mult_op : MUL | DIV | MOD ;
add_op : ADD | SUB ;
shift_op : SHR | SHL ;
bit_op : ANDA | ORA | XORA ;

/*
 * Lexer Rules
 */

ENTRYPOINT : 'entrypoint' ;
DEF : 'def' ;
VAR : 'var' ;
TRUE : 'true' ;
FALSE : 'false' ;
RETURN : 'return' ;

IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;

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

