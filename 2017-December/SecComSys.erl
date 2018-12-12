% hello world program
-module(helloworld).
-export([start/0, receiver/3, sender/1, registry/1]).

% ----- CRYPTO -----

keygen() ->
  PublicKey = rand:uniform(25)+1, % 19,
  PrivateKey = 26 - PublicKey,
  io:fwrite("public key: "),
  io:write(PublicKey),
  io:fwrite("\nprivate key: "),
  io:write(PrivateKey),
  io:fwrite("\n"),
  {PublicKey, PrivateKey}.
      
encrypt([], _) -> [];
encrypt([C|S], Key) -> [$A + (C - $A + Key) rem 26|encrypt(S,Key)]. 
      
% ----- REGISTRY -----

getkey([], Pid) -> throw({notfound,Pid});
getkey([{Pid,Key}|_], Pid) -> Key;
getkey([_|Map], Pid) -> getkey(Map,Pid).

putkey(Map, Pid, Key) -> [{Pid, Key}|Map].

registry(Map) -> 
  receive 
    {register, Pid} -> 
      {PublicKey, PrivateKey} = keygen(),
      Pid ! {keypair, {PublicKey, PrivateKey}},
      registry(putkey(Map, Pid, PublicKey));
    {lookup, Pid, Return} ->
      Return ! {pubkey, Pid, getkey(Map, Pid)}
  end,
  registry(Map).

receiver(Registry, PublicKey, PrivateKey) ->
  receive
    {init, R} ->
      R ! {register, self()},
      receiver(R, PublicKey, PrivateKey);
    {keypair, {Key1, Key2}} ->
      receiver(Registry, Key1, Key2);
    {message, Y} ->
      X = encrypt(Y, PrivateKey),
      io:fwrite("decrypted: '" ++ X ++ "'\n")
  end,
  receiver(Registry, PublicKey, PrivateKey).

sender(Registry) ->
  receive
    {init, R} ->
      sender(R); % save registry
    {comm, Pid} ->
      Registry ! {lookup, Pid, self()}; % ask registry for pub key
    {pubkey, Recipient, PublicKey} ->
      X = "SECRET",
      io:fwrite("cleartext: '" ++ X ++ "'\n"),
      Y = encrypt(X, PublicKey),
      io:fwrite("encrypted: '" ++ Y ++ "'\n"),
      Recipient ! {message, Y}
  end,
  sender(Registry).

start() ->
  Registry = spawn(helloworld, registry, [[]]),
  Receiver = spawn(helloworld, receiver, [0,0,0]),
  Receiver ! {init, Registry},
  Sender = spawn(helloworld, sender, [0]),
  Sender ! {init, Registry},
  Sender ! {comm, Receiver}.
