var score = 0,
		time = 0,
		talk = 0,
	$score = $("#score"),
	$time = $("#time"),
	$talk = $("#talk"),
	$player = $("#player"),
	$badman = $("#badman"),
	punchready = 1,
	timer = null;

var a_discour = document.getElementById("a_discour"),
    a_punch = document.getElementById("a_punch");

$player.on("click", ".rightarm", function() {
	var $this = $(this);
	if(punchready){
		$badman.removeClass('left');
		$badman.addClass('right');
		timeanim($this,"punch",200);
		bm_punched();
	}
});
$player.on("click", ".leftarm", function() {
	var $this = $(this);
	if(punchready){
		$badman.removeClass('right');
		$badman.addClass('left');
		timeanim($this,"punch",200);
		bm_punched();
	}
});

function bm_punched() {
	punchready = 0;
	punch_audio('play');
	$badman.removeClass('talk');
	discour_audio('pause');
	setTimeout(function(){discour_audio('play');},1000);
	timeanim($badman,"punched",200);
	score+=10;
	updatescore();
	if(score%100 == 0){
		timeanim($badman,"spin",200);
	}
}
function timeanim(el,animclass,timeout){
		el.addClass(animclass);
		setTimeout(function(){
			el.removeClass(animclass);		
			if(animclass == 'punched'){
				punchready = 1;
			}
		}, timeout);
}

function timing() {
  time++;
	var timedisp = parseInt(time/4);
	$time.html(timedisp);
	if(!$badman.hasClass('talk')){
		$badman.addClass('talk');
	}else{
		talk++;
		brain = 100 - talk;
		if(brain < 1){
			gameover();
		}
		$talk.css({height:brain+'%'});
	}
}
function updatescore() {
	$score.html(score);
}
function gameover(){
	clearTimeout(timer);
	$player.unbind("click");
	$player.addClass('hidden');
$("#overlay").removeClass("hidden");
	$("#overlay .score").html(score*time + ' points');
	clearInterval(timer);
}
function init() {
	timer = setInterval(timing, 250);
	updatescore();
	discour_audio();
}

document.addEventListener("DOMContentLoaded", function() {
	$("#overlay > .gameover").addClass("hidden");
	Pace.on("done", function(){
		$("#overlay > .start").removeClass("hidden");
	});
});
function discour_audio(state='play') {
  switch (state) {
			case 'play':
		    a_discour.play();
			break;
			case 'pause':
		    a_discour.pause();
			break;
  } 
}
function punch_audio(state='play') {
  switch (state) {
			case 'play':
		    a_punch.play();
			break;
			case 'pause':
		    a_punch.pause();
			break;
  } 
}
function mute_toggle(){
	if(a_discour.muted){
	a_discour.muted = false;
	a_punch.muted = false;
 }else{
	a_discour.muted = true;
	a_punch.muted = true;
	}
}

$('#mute').on("click", function() {
	mute_toggle();
	$(this).toggleClass('on');
});

$('#start').on("click", function() {
	$("#overlay").addClass("hidden");
	$("#overlay .start").addClass("hidden");
	$("#overlay .gameover").removeClass("hidden");
	init();
});


