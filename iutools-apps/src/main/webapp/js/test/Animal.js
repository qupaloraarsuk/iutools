class Animal {
  constructor(name) {
    this.speed = 0;
    this.name = name;
  }
  run(speed) {
    this.speed += speed;
    console.log(`${this.name} runs with speed ${this.speed} (Animal impl.).`);
  }
  stop() {
    this.speed = 0;
    console.log(`${this.name} stopped (Animal impl.).`);
  }
  jump() {
	    console.log(`${this.name} jumped (Animal impl.).`);
  }
}