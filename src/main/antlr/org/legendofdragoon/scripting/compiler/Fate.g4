grammar Fate ;

/*
 * Parser Rules
 */

body : entrypoint+ function+ ;

entrypoint : ENTRYPOINT IDENTIFIER TERM ;
function : DEF IDENTIFIER identifier_list block ;

if : IF LPAREN expression RPAREN block ;
while : WHILE LPAREN expression RPAREN block ;
control : if | while ;

block : OPENER (statement | control)* CLOSER ;

statement : (declaration | assignment | postfix | call | return) TERM ;
postfix : IDENTIFIER postfix_op ;
assignment : (IDENTIFIER | identifier_list) ASSIGN expression ;
declaration : VAR (IDENTIFIER | identifier_list) (ASSIGN expression)? ;

call : IDENTIFIER (SCOPE IDENTIFIER)? expression_list ;
return : RETURN (expression | expression_list)? ;

identifier_list : LPAREN (IDENTIFIER (COMMA IDENTIFIER)*)? RPAREN ;
expression_list : LPAREN (expression (COMMA expression)*)? RPAREN ;
value_list : LPAREN (value (COMMA value)*)? RPAREN ;

expression :
  LPAREN expression RPAREN |
  (ADD | SUB | NOTC | NOTA) expression |
  expression comp_op expression |
  expression mult_op expression |
  expression add_op expression |
  expression shift_op expression |
  expression bit_op expression |
  value ;

value : IDENTIFIER | NUMBER | call ;

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

OPENER : '{' ;
CLOSER : '}' ;
TERM : ';' ;

SCOPE : '::' ;

IDENTIFIER : [a-zA-Z_][a-zA-Z0-9_]* ;
NUMBER : [0-9]+ ;
WHITESPACE : [ \n] -> skip ;
